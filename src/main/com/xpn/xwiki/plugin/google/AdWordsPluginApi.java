/**
 * ===================================================================
 *
 * Copyright (c) 2003,2004 Ludovic Dubost, All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details, published at 
 * http://www.gnu.org/copyleft/lesser.html or in lesser.txt in the
 * root folder of this distribution.

 * Created by
 * User: Ludovic Dubost
 * Date: 23 avr. 2005
 * Time: 00:57:33
 */
package com.xpn.xwiki.plugin.google;

import com.google.api.adwords.v2.*;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.api.Api;
import org.apache.axis.client.Stub;

import javax.xml.rpc.ServiceException;
import java.rmi.RemoteException;
import java.util.Calendar;
import java.util.Date;

public class AdWordsPluginApi extends Api {
    private AdWordsPlugin plugin;

    /** The namespace used for API headers. **/
    private static final String apiNS="https://adwords.google.com/api/adwords/v2";

    public AdWordsPluginApi(AdWordsPlugin plugin, XWikiContext context) {
            super(context);
            setPlugin(plugin);
        }

    public AdWordsPlugin getPlugin() {
        return plugin;
    }

    public void setPlugin(AdWordsPlugin plugin) {
        this.plugin = plugin;
    }

    public KeywordRequest newKeywordRequest() {
        return new KeywordRequest();
    }

    private void addSecurity(Stub stub, String email, String password, String token) {
        // Set the authentication request headers
        stub.setHeader(apiNS, "email", email);
        stub.setHeader(apiNS, "password", password);
        stub.setHeader(apiNS, "useragent", "XWiki AdWords Plugin version 1.0");
        stub.setHeader(apiNS, "token", token);
    }


    public KeywordEstimate[] getTrafficEstimate(String token, String email, String password, KeywordRequest[] kr) throws ServiceException, RemoteException {
        // Make a service
        TrafficEstimatorService service = new TrafficEstimatorServiceLocator();
        // Now use the service to get a stub to the Service Definition Interface (SDI)
        TrafficEstimatorInterface traffic = service.getTrafficEstimatorService();

        addSecurity((Stub)traffic, email, password, token);

        return traffic.estimateKeywordList(kr);
    }


    public Campaign[] getCampaignList(String token, String email, String password, int [] ids) throws ServiceException, RemoteException {
        // Make a service
        CampaignServiceService service = new CampaignServiceServiceLocator();
        // Now use the service to get a stub to the Service Definition Interface (SDI)
        CampaignService campaign = service.getCampaignService();

        addSecurity((Stub)campaign, email, password, token);
        return campaign.getCampaignList(ids);
    }

    public StatsRecord[] getCampaignList(String token, String email, String password, int [] ids, Date startDate, Date endDate) throws ServiceException, RemoteException {
        // Make a service
        CampaignServiceService service = new CampaignServiceServiceLocator();
        // Now use the service to get a stub to the Service Definition Interface (SDI)
        CampaignService campaign = service.getCampaignService();

        addSecurity((Stub)campaign, email, password, token);

        Calendar startcal = Calendar.getInstance();
        Calendar endcal = Calendar.getInstance();
        startcal.setTime(startDate);
        endcal.setTime(endDate);
        return campaign.getCampaignStats(ids, startcal, endcal);
    }


}
