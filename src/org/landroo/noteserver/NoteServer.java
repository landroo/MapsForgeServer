package org.landroo.noteserver;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class NoteServer 
{
	private static final String TAG = "NoteServer";

	private String ip;

	public int port = 8080;
	public String root = "/root";
	public String tileRoot = "mapsforge_tiles";
	public String mapFiles = "/maps/hungary.map;";
	public String renderXML = "/maps/rendertheme-v4.xml";

	private static int iLang = 0;
	
	private static String userHome = System.getProperty( "user.home" );
	
	public final static void main(String[] args)
	{
		NoteServer ns = new NoteServer();
		
		// load ini file
		File iniFile = new File("NoteServer.ini");
		if(iniFile.exists())
		{
			Ini ini;
			try 
			{
				ini = new Ini("NoteServer.ini");
				ns.port = Integer.parseInt(ini.get("HTTP", "port"));
				ns.root = userHome + ini.get("HTTP", "root");
				
				ns.tileRoot = ini.get("TileServer", "tileRoot");
				ns.mapFiles = ini.get("TileServer", "mapFiles");
				ns.renderXML = userHome + ini.get("TileServer", "renderXML");
				
				Log.i(TAG, "Tile root: " + ns.tileRoot);
				Log.i(TAG, "Map file: " + ns.mapFiles);
				Log.i(TAG, "Render XML: " + ns.renderXML);
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
					
		}
		
		// start http server
		ns.startServer();

		// wait for requests
		try
		{
			System.in.read();
		}
		catch (Throwable t)
		{
		}
		if (iLang == 0)
		{
			Log.i(TAG, "Server stopped!");
		}
		else
		{
			Log.i(TAG, "A kiszolgáló leállt!");
		}

	}
	
	//
	public void startServer()
	{
		ip = getLocalIpAddress();
		if (iLang == 0)
		{
			if (ip.equals("")) addLog("Please connect to internet!");
			else addLog("Note server at http://" + ip + ":" + port);
		}
		else
		{
			if (ip.equals("")) addLog("Kérem kapcsolódjon az internetre!");
			else addLog("Note server a http://" + ip + ":" + port + " címen");
		}

		try
		{
			HttpServer httpSetver = new HttpServer(port, root, tileRoot, mapFiles, renderXML, userHome);
		}
		catch (IOException ioe)
		{
			Log.i(TAG, "Couldn't start server: " + ioe);
		}
	}
	
	private String getLocalIpAddress()
	{
		String ip = "";
		try 
		{
			ip = InetAddress.getLocalHost().getHostAddress();
		} 
		catch (UnknownHostException e) 
		{
			addLog("" + e);
		}
		
		return ip;
	}
	
	private void addLog(String log)
	{
		Log.i(TAG, log);
	}
	
	public static class Log
	{
		public static void i(String tag, String info)
		{
	        System.out.print(tag + "\t" + info + "\n");	
		}
	}
}
