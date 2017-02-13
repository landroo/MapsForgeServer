package org.landroo.noteserver;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.imageio.ImageIO;

import org.landroo.noteserver.NoteServer.Log;
import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.core.model.Tag;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.awt.graphics.AwtBitmap;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.datastore.MapReadResult;
import org.mapsforge.map.datastore.PointOfInterest;
import org.mapsforge.map.datastore.Way;
import org.mapsforge.map.layer.cache.FileSystemTileCache;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.layer.renderer.RendererJob;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.reader.header.MapFileInfo;
import org.mapsforge.map.rendertheme.ExternalRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

public class HttpServer extends NanoHTTPD {
	private static final String TAG = "HttpServer";

	private Format formatter = new SimpleDateFormat("HH:mm:ss");

	private String root = "";

	public String tileRoot = "mapsforge_tiles";
	public String mapFiles = "/maps/hungary.map;";
	public String renderXML = "/maps/rendertheme-v4.xml";

	private String lastFile = "";

	private boolean logReq = true;
	
	private String userHome; 

	public HttpServer(int port, String wwwroot, String tileRoot,
			String mapFiles, String renderXML, String Home) throws IOException {
		super(port);// , new File("."));

		this.root = wwwroot;
		this.tileRoot = tileRoot;
		this.mapFiles = mapFiles;
		this.renderXML = renderXML;
		this.userHome = Home;

		this.myFileDir = new File(wwwroot);
	}

	@Override
	public Response serve(String uri, String method, Properties header,
			Properties parms, Properties files, String post) {
		// Log.i(TAG, uri);
		if (logReq) {
			Date d = new Date();
			byte[] bytes = method.getBytes();
			boolean bText = true;
			for (int i = 0; i < bytes.length; i++) {
				if (bytes[i] < 32 || bytes[i] > 'z') {
					bText = false;
					break;
				}
			}

			if (bText) {
				showLog(formatter.format(d.getTime()) + " HTTP " + method
						+ "->" + uri + "->" + parms);
			} else {
				String log = formatter.format(d.getTime()) + " HTTP (";
				for (int i = 0; i < bytes.length; i++)
					log += String.format("%X", bytes[i]) + " ";
				showLog(log + ")");
			}
		}

		// mapsforge_tiles
		if (uri.indexOf(tileRoot) != -1) {
			Log.i(TAG, uri);
			getTile(uri);
		}

		if (post != null && post.indexOf("latlng:") != -1) {
			String latlng = post.substring(7);
			String[] pos = latlng.split(",");
			double lat = Double.parseDouble(pos[0]);
			double lon = Double.parseDouble(pos[1]);
			int zoom = Integer.parseInt(pos[2]);

			String[] mapFileArr = mapFiles.split(";");
			String res = findInFile(lat, lon, (byte) zoom, userHome + mapFileArr[1]);

			Log.i(TAG, "" + res);

			return new NanoHTTPD.Response(HTTP_OK, MIME_HTML, res);
		}

		return serveFile(uri, header, this.myFileDir, true);
	}

	private void showLog(String sAlert) {
		NoteServer.Log.i(TAG, sAlert);
	}

	// http://yourserveraddress/osm_tiles/{z}/{x}/{y}.png
	// http://192.168.0.46:8080/mapsforge_tiles/9/282/178.png
	// /mapsforge_tiles/9/282/178.png
	private boolean getTile(String uri) {
		// getNewTile(2263, 1432,
		// (byte)12,"/noteServerRoot/mapsforge_tiles/12/2263/1432.png");

		File tileFile = new File(root + uri);
		if (tileFile.exists())
			return true;
		else {
			try {
				String[] tileArr = uri.replace(".", "/").split("/");
				if (tileArr.length > 4) {
					int x = Integer.parseInt(tileArr[3]);
					int y = Integer.parseInt(tileArr[4]);
					int l = Integer.parseInt(tileArr[2]);
					String path = root + "/" + tileArr[1] + "/" + tileArr[2]
							+ "/" + tileArr[3];
					File dir = new File(path);
					dir.mkdirs();

					return getNewTile(x, y, (byte) l, root + uri);
				} else
					return false;
			} catch (Exception ex) {
				Log.i(TAG, "" + ex);
				return false;
			}
		}
	}

	private synchronized boolean getNewTile(int tileX, int tileY, byte zoomLevel, String tileFile) 
	{
		if (lastFile.equals(tileFile))
			return true;
		lastFile = tileFile;

		try 
		{
			Tile tile = new Tile(tileX, tileY, zoomLevel, 256);
			String[] mapFileArr = mapFiles.split(";");
			for (int i = 0; i < mapFileArr.length; i++) 
			{
				File file = new File(userHome + mapFileArr[i]);
				MapFile mapFile = new MapFile(file);
				MapFileInfo mapInfo = mapFile.getMapFileInfo();
				if (mapFile.supportsTile(tile)) 
				{
					// NoteServer.Log.i(TAG, "zoomLevelMin: " + mapInfo.zoomLevelMin + " zoomLevelMax: " + mapInfo.zoomLevelMax);
					int tileSize = mapInfo.tilePixelSize;
					tile = new Tile(tileX, tileY, zoomLevel, tileSize);
					AwtGraphicFactory graphicFactory = (AwtGraphicFactory) AwtGraphicFactory.INSTANCE;
					File cache = new File(file.getParent(), "cache");
					TileCache tileCache = new FileSystemTileCache(16384, cache,	graphicFactory);
					DatabaseRenderer renderer = new DatabaseRenderer(mapFile, graphicFactory, tileCache);

					DisplayModel displayModel = new DisplayModel();
					File renderThemeFile = new File(renderXML);
					XmlRenderTheme xmlRenderTheme = new ExternalRenderTheme(renderThemeFile);
					RenderThemeFuture renderThemeFuture = new RenderThemeFuture(graphicFactory, xmlRenderTheme, displayModel);

					RendererJob rendererJob = new RendererJob(tile, mapFile, renderThemeFuture, displayModel, .9f, true, false);
					TileBitmap tileBitmap = renderer.executeJob(rendererJob, xmlRenderTheme);
					//TileBitmap tileBitmap = renderer.executeJob(rendererJob);

					File outFile = new File(tileFile);
					if (outFile.exists()) 
					{
						mergeTiles(tileFile, tileBitmap);
					} 
					else 
					{
						OutputStream outputStream = new FileOutputStream(tileFile);
						tileBitmap.compress(outputStream);
						outputStream.close();
					}
				}
				mapFile.close();
			}
		} catch (Exception e) {
			NoteServer.Log.i(TAG, "" + e + " " + tileFile);
			return false;
		}

		// NoteServer.Log.i(TAG, tileFile);

		return true;
	}

	private void mergeTiles(String tileFile, TileBitmap tileBitmap) {
		try {
			// read existing into a buffer
			File inFile = new File(tileFile);
			BufferedImage img = ImageIO.read(inFile);

			Graphics g = img.getGraphics();
			g.drawImage(((AwtBitmap) tileBitmap).getImage(), 0, 0, null);

			OutputStream outputStream = new FileOutputStream(tileFile);
			ImageIO.write(img, "png", outputStream);
			outputStream.close();

			// NoteServer.Log.i(TAG, tileFile + " merged.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String findInFile(double lat, double lon, byte zoom, String fileName) 
	{
		WGS84Point inPnt, tmpPnt;
		
		inPnt = webMercatorLocation(lat, lon);
		
		List<String> list = new ArrayList<String>();
		List<String> latlons = new ArrayList<String>();

		int xtile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
		int ytile = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));

		Tile tile = new Tile(xtile, ytile, zoom, 256);
		File file = new File(fileName);
		MapFile mapFile = new MapFile(file);
		MapReadResult mapResult = mapFile.readMapData(tile);
		for (Way way : mapResult.ways) 
		{
			Log.i(TAG, "num of latLongs: " + way.latLongs.length + " "	+ way.latLongs[0].length);
			for (Tag tag : way.tags) 
			{
				// Log.i(TAG, "" + way.latLongs[0].length);
				if (tag.key.equals("name") && list.size() < 50 && !list.contains(tag.value)) 
				{
					Log.i(TAG, "" + tag.value);
					list.add(tag.value);
				}
			}
		}

		for (PointOfInterest poi : mapResult.pointOfInterests) 
		{
			for (Tag tag : poi.tags) 
			{
				if (tag.key.equals("name") && list.size() < 50 && !list.contains(tag.value)) 
				{
					list.add(tag.value);
				}
			}
		}

		mapFile.close();

		if (list.size() > 50)
			list.add("...");

		return list.toString();
	}

	public static String getTileNumber(final double lat, final double lon, final int zoom) 
	{
		int xtile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
		int ytile = (int) Math
				.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1
						/ Math.cos(Math.toRadians(lat)))
						/ Math.PI)
						/ 2 * (1 << zoom));

		if (xtile < 0) xtile = 0;
		if (xtile >= (1 << zoom)) xtile = ((1 << zoom) - 1);
		if (ytile < 0) ytile = 0;
		if (ytile >= (1 << zoom)) ytile = ((1 << zoom) - 1);

		return ("" + zoom + "/" + xtile + "/" + ytile);
	}

	public class BoundingBox 
	{
		double north;
		double south;
		double east;
		double west;
	}

	public BoundingBox tile2boundingBox(final int x, final int y, final int zoom) 
	{
		BoundingBox bb = new BoundingBox();
		bb.north = tile2lat(y, zoom);
		bb.south = tile2lat(y + 1, zoom);
		bb.west = tile2lon(x, zoom);
		bb.east = tile2lon(x + 1, zoom);

		return bb;
	}

	public static double tile2lon(int x, int z) 
	{
		return x / Math.pow(2.0, z) * 360.0 - 180;
	}

	public static double tile2lat(int y, int z) 
	{
		double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);

		return Math.toDegrees(Math.atan(Math.sinh(n)));
	}

	//
	public double Distance(double x, double y, double x1, double y1, double x2, double y2) 
	{
		double A = x - x1;
		double B = y - y1;
		double C = x2 - x1;
		double D = y2 - y1;

		double dot = A * C + B * D;
		double len_sq = C * C + D * D;
		double param = -1;
		if (len_sq != 0) // in case of 0 length line
			param = dot / len_sq;

		double xx, yy;

		if (param < 0) 
		{
			xx = x1;
			yy = y1;
		} 
		else if (param > 1) 
		{
			xx = x2;
			yy = y2;
		} 
		else 
		{
			xx = x1 + param * C;
			yy = y1 + param * D;
		}

		double dx = x - xx;
		double dy = y - yy;

		return Math.sqrt(dx * dx + dy * dy);
	}

	// calculate distance between two latitude and longitude
	public double distFrom(double lat1, double lng1, double lat2, double lng2) 
	{
		double earthRadius = 3958.75;
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2)
				* Math.sin(dLng / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double dist = earthRadius * c;

		int meterConversion = 1609;

		return dist * meterConversion;
	}
	
	public class GeoPoint
	{
		public double lon;
		public double lat;
		public double alt;

		public GeoPoint(double lat, double lon, double alt)
		{
			this.lon = lon;
			this.lat = lat;
			this.alt = alt;
		}
	}	
	
	private class WGS84Point
	{
		public double x;
		public double y;
		public double z;

		public WGS84Point(double x, double y, double z)
		{
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}	
	
	public WGS84Point webMercatorLocation(double lat, double lon)
	{
		double y_coeff = 6378130; // earth radius in meter
		double x_coeff = y_coeff * 2 * Math.PI / 360;
		double x = x_coeff * lon;
		double y = y_coeff * Math.log(Math.tan(Math.PI / 4 + (Math.PI * lat / 180) / 2));

		return new WGS84Point(x, y, 0);
	}

}
