package org.landroo.noteserver;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

public class Ini
{
	private String iniName;
	
	public Ini(String fileName)
	{
		this.iniName = fileName;
	}
	
	public String get(String group, String var)
	{
		String line, ret = "";
		boolean bOK = false;
		String[] pair;
		try 
		{
		    InputStream fis = new FileInputStream(iniName);
		    InputStreamReader isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
		    BufferedReader br = new BufferedReader(isr);
		 
		    while ((line = br.readLine()) != null) 
		    {
		        // Deal with the line
		    	if(bOK)
		    	{
		    		pair = line.split("=");
		    		if(pair[0].equals(var))
		    		{
		    			ret = pair[1];
		    			break;
		    		}
		    	}
		    	if(!line.equals("") && line.substring(0, 1).equals("["))
		    		bOK = false;
		    	if(line.equals("[" + group + "]"))
		    		bOK = true;
		    }
		    
		    br.close();
		}
		catch(Exception ex)
		{
			
		}

		return ret;
	}
}
