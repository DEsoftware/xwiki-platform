/**
 * ===================================================================
 *
 * Copyright (c) 2003-2005 XpertNet, All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details, published at
 * http://www.gnu.org/copyleft/gpl.html or in gpl.txt in the
 * root folder of this distribution.
 *
 */

package com.xpn.xwiki.web.includeservletasstring;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author LBlaze
 */
public class IncludeServletAsString {
  
    private static final Log log = LogFactory.getLog(IncludeServletAsString.class);
  
    /**
     * Creates a new instance of IncludeServletAsString 
     */
    private IncludeServletAsString() {
    }
    
    
    static public String invokeServletAndReturnAsString(String url,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse)
    throws IOException, ServletException {
      
        if( log.isDebugEnabled() )
        {
            log.debug("Including url \""+url+"\"...");
        }
      
        RequestDispatcher requestDispatcher = servletRequest.getRequestDispatcher(url);
        
        if( requestDispatcher == null )
        {
            IllegalArgumentException iae = new IllegalArgumentException(
                    "Failed to get RequestDispatcher for url: "+url);
            log.error(iae.getMessage(),iae);
            throw iae;
        }
      
        BufferedResponse bufferedResponse = new BufferedResponse(servletResponse);
        
        requestDispatcher.include(servletRequest, bufferedResponse);
        
        byte[] buffer = bufferedResponse.getBufferAsByteArray();
        if( log.isDebugEnabled() )
        {
            log.debug("Buffer returned with "+buffer.length+" bytes.");
        }
        
        String bufferString = new String(buffer, servletResponse.getCharacterEncoding());
     
        return bufferString;
    }
  
}
