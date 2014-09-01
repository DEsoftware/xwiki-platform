/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.util.Util;

/**
 * Handle URL generation in rendered wiki pages. This implementation makes sure URL will be local URL for exported
 * content (like skin, attachment and pages).
 * 
 * @version $Id$
 */
public class ExportURLFactory extends XWikiServletURLFactory
{
    /**
     * Logging tool.
     */
    protected static final Logger LOGGER = LoggerFactory.getLogger(ExportURLFactory.class);

    /** The encoding to use when reading text resources from the filesystem and when sending css/javascript responses. */
    private static final String ENCODING = "UTF-8";

    private static final SkinAction SKINACTION = new SkinAction();

    // TODO: use real css parser
    private static Pattern CSSIMPORT = Pattern.compile("^\\s*@import\\s*\"(.*)\"\\s*;$", Pattern.MULTILINE);

    private ExportURLFactoryContext factoryContext = new ExportURLFactoryContext();

    /**
     * ExportURLFactory constructor.
     */
    public ExportURLFactory()
    {
    }

    public ExportURLFactoryContext getExportURLFactoryContext()
    {
        return this.factoryContext;
    }

    /**
     * Init the url factory.
     * 
     * @param exportedPages the pages that will be exported.
     * @param exportDir the directory where to copy exported objects (attachments).
     * @param context the XWiki context.
     */
    public void init(Collection<String> exportedPages, File exportDir, XWikiContext context)
    {
        super.init(context);

        if (exportDir != null) {
            getExportURLFactoryContext().setExportDir(exportDir);
        }

        if (exportedPages != null) {
            XWikiDocument doc = new XWikiDocument();

            for (String pageName : exportedPages) {
                doc.setDatabase(null);
                doc.setSpace(null);
                doc.setName(null);

                doc.setFullName(pageName);

                String absolutePageName = "";

                if (doc.getDatabase() != null) {
                    absolutePageName += doc.getDatabase().toLowerCase();
                } else {
                    absolutePageName += context.getWikiId().toLowerCase();
                }

                absolutePageName += XWikiDocument.DB_SPACE_SEP;

                absolutePageName += doc.getFullName();

                getExportURLFactoryContext().addExportedPage(absolutePageName);
            }
        }
    }

    @Override
    public URL createSkinURL(String filename, String skin, XWikiContext context)
    {
        try {
            getExportURLFactoryContext().addNeededSkin(skin);

            StringBuffer newPath = new StringBuffer("file://");

            // Adjust path for links inside CSS files (since they need to be relative to the CSS file they're in).
            getExportURLFactoryContext().adjustCSSPath(newPath);

            newPath.append("skins/");
            newPath.append(skin);

            addFileName(newPath, filename, false, context);

            return new URL(newPath.toString());
        } catch (Exception e) {
            LOGGER.error("Failed to create skin URL", e);
        }

        return super.createSkinURL(filename, skin, context);
    }

    @Override
    public URL createSkinURL(String filename, String web, String name, XWikiContext context)
    {
        return createSkinURL(filename, web, name, null, context, false);
    }

    public URL createSkinURL(String filename, String web, String name, XWikiContext context, boolean skipSkinDirectory)
    {
        return createSkinURL(filename, web, name, null, context, skipSkinDirectory);
    }

    @Override
    public URL createSkinURL(String fileName, String web, String name, String wikiId, XWikiContext context)
    {
        return createSkinURL(fileName, web, name, wikiId, context, false);
    }

    public URL createSkinURL(String fileName, String web, String name, String wikiId, XWikiContext context,
        boolean skipSkinDirectory)
    {
        URL skinURL;
        if (wikiId == null) {
            skinURL = super.createSkinURL(fileName, web, name, context);
        } else {
            skinURL = super.createSkinURL(fileName, web, name, wikiId, context);
        }

        if (!"skins".equals(web)) {
            return skinURL;
        }

        try {
            getExportURLFactoryContext().addNeededSkin(name);

            StringBuffer filePathBuffer = new StringBuffer();
            if (!skipSkinDirectory) {
                filePathBuffer.append("skins/");
                filePathBuffer.append(name);
                filePathBuffer.append("/");
            }
            filePathBuffer.append(fileName);

            String filePath = filePathBuffer.toString();

            if (!getExportURLFactoryContext().hasExportedSkinFile(filePath)) {
                getExportURLFactoryContext().addExportedSkinFile(filePath);

                File file = new File(getExportURLFactoryContext().getExportDir(), filePath);

                // Make sure the folder exists
                File folder = file.getParentFile();
                if (!folder.exists()) {
                    folder.mkdirs();
                }

                FileOutputStream fos = new FileOutputStream(file);
                String database = context.getWikiId();

                try {
                    XWikiServletResponseStub response = new XWikiServletResponseStub();
                    response.setOutpuStream(fos);
                    context.setResponse(response);
                    if (wikiId != null) {
                        context.setWikiId(wikiId);
                    }

                    // Adjust path for links inside CSS files.
                    getExportURLFactoryContext().pushCSSPathAdjustment(StringUtils.countMatches(filePath, "/"));
                    try {
                        SKINACTION.render(skinURL.getPath(), context);
                    } finally {
                        getExportURLFactoryContext().popCSSPathAdjustement();
                    }
                } finally {
                    fos.close();
                    if (wikiId != null) {
                        context.setWikiId(database);
                    }
                }

                followCssImports(file, web, name, wikiId, context);
            }

            StringBuffer newPath = new StringBuffer("file://");

            // Adjust path for links inside CSS files (since they need to be relative to the CSS file they're in).
            getExportURLFactoryContext().adjustCSSPath(newPath);

            newPath.append(filePath);

            skinURL = new URL(newPath.toString());
        } catch (Exception e) {
            LOGGER.error("Failed to create skin URL", e);
        }

        return skinURL;
    }

    /**
     * Resolve CSS <code>@import</code> targets.
     */
    private void followCssImports(File file, String web, String name, String wikiId, XWikiContext context)
        throws IOException
    {
        // TODO: find better way to know it's css file (not sure it's possible, we could also try to find @import
        // whatever the content)
        if (file.getName().endsWith(".css")) {
            FileInputStream fis = new FileInputStream(file);

            try {
                String content = IOUtils.toString(fis, ENCODING);

                // TODO: use real css parser
                Matcher matcher = CSSIMPORT.matcher(content);

                while (matcher.find()) {
                    String fileName = matcher.group(1);

                    // Adjust path for links inside CSS files.
                    while(fileName.startsWith("../")) {
                        fileName = StringUtils.removeStart(fileName, "../");
                    }

                    if (wikiId == null) {
                        createSkinURL(fileName, web, name, context, true);
                    } else {
                        createSkinURL(fileName, web, name, wikiId, context, true);
                    }
                }
            } finally {
                fis.close();
            }
        }
    }

    @Override
    public URL createResourceURL(String filename, boolean forceSkinAction, XWikiContext context)
    {
        try {
            StringBuffer newPath = new StringBuffer("file://");

            // Adjust path for links inside CSS files (since they need to be relative to the CSS file they're in).
            getExportURLFactoryContext().adjustCSSPath(newPath);

            newPath.append("resources");

            addFileName(newPath, filename, false, context);

            return new URL(newPath.toString());
        } catch (Exception e) {
            LOGGER.error("Failed to create skin URL", e);
        }

        return super.createResourceURL(filename, forceSkinAction, context);
    }

    @Override
    public URL createURL(String web, String name, String action, String querystring, String anchor, String xwikidb,
        XWikiContext context)
    {
        try {
            // Look for a special handler for the passed action
            try {
                ExportURLFactoryActionHandler handler = Utils.getComponent(ExportURLFactoryActionHandler.class, action);
                return handler.createURL(web, name, querystring, anchor, xwikidb, context,
                    getExportURLFactoryContext());
            } catch (Exception e) {
                // Failed to find such a component or it doesn't work, simply ignore it and continue with the default
                // behavior!
            }

            String wikiname = xwikidb == null ? context.getWikiId().toLowerCase() : xwikidb.toLowerCase();

            if (getExportURLFactoryContext().hasExportedPage(wikiname + XWikiDocument.DB_SPACE_SEP + web
                + XWikiDocument.SPACE_NAME_SEP + name)
                && "view".equals(action) && context.getLinksAction() == null)
            {
                StringBuffer newpath = new StringBuffer();

                newpath.append("file://");

                newpath.append(wikiname);
                newpath.append(".");
                newpath.append(web);
                newpath.append(".");
                newpath.append(name);

                newpath.append(".html");

                if (!StringUtils.isEmpty(anchor)) {
                    newpath.append("#");
                    newpath.append(anchor);
                }

                return new URL(newpath.toString());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create page URL", e);
        }

        return super.createURL(web, name, action, querystring, anchor, xwikidb, context);
    }

    /**
     * Generate an url targeting attachment in provided wiki page.
     * 
     * @param filename the name of the attachment.
     * @param space the space of the page containing the attachment.
     * @param name the name of the page containing the attachment.
     * @param xwikidb the wiki of the page containing the attachment.
     * @param context the XWiki context.
     * @return the generated url.
     * @throws XWikiException error when retrieving document attachment.
     * @throws IOException error when retrieving document attachment.
     * @throws URISyntaxException when retrieving document attachment.
     */
    private URL createAttachmentURL(String filename, String space, String name, String xwikidb, XWikiContext context)
        throws XWikiException, IOException, URISyntaxException
    {
        String db = (xwikidb == null ? context.getWikiId() : xwikidb);
        String path = "attachment/" + db + "." + space + "." + name + "." + filename;

        File file = new File(getExportURLFactoryContext().getExportDir(), path);
        if (!file.exists()) {
            XWikiDocument doc =
                context.getWiki().getDocument(
                    db + XWikiDocument.DB_SPACE_SEP + space + XWikiDocument.SPACE_NAME_SEP + name, context);
            XWikiAttachment attachment = doc.getAttachment(filename);
            FileOutputStream fos = new FileOutputStream(file);
            IOUtils.copy(attachment.getContentInputStream(context), fos);
            fos.close();
        }

        StringBuffer newPath = new StringBuffer("file://");

        // Adjust path for links inside CSS files (since they need to be relative to the CSS file they're in).
        getExportURLFactoryContext().adjustCSSPath(newPath);

        newPath.append(path.replace(" ", "%20"));

        return new URI(newPath.toString()).toURL();
    }

    @Override
    public URL createAttachmentURL(String filename, String web, String name, String action, String querystring,
        String xwikidb, XWikiContext context)
    {
        try {
            return createAttachmentURL(filename, web, name, xwikidb, context);
        } catch (Exception e) {
            LOGGER.error("Failed to create attachment URL", e);

            return super.createAttachmentURL(filename, web, name, action, null, xwikidb, context);
        }
    }

    @Override
    public URL createAttachmentRevisionURL(String filename, String web, String name, String revision, String xwikidb,
        XWikiContext context)
    {
        try {
            return createAttachmentURL(filename, web, name, xwikidb, context);
        } catch (Exception e) {
            LOGGER.error("Failed to create attachment URL", e);

            return super.createAttachmentRevisionURL(filename, web, name, revision, xwikidb, context);
        }
    }

    @Override
    public String getURL(URL url, XWikiContext context)
    {
        if (url == null) {
            return "";
        }

        String path = Util.escapeURL(url.toString());

        if (url.getProtocol().equals("file")) {
            path = path.substring("file://".length());
        }

        return path;
    }
}
