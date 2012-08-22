/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.repo.webdav;

import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.permissions.AccessDeniedException;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.dom4j.DocumentHelper;
import org.dom4j.io.XMLWriter;
import org.xml.sax.Attributes;

/**
 * Implements the WebDAV MOVE method
 * 
 * @author Derek Hulley
 */
public class MoveMethod extends AbstractMoveOrCopyMethod
{
    /**
     * Default constructor
     */
    public MoveMethod()
    {
    }

    protected void moveOrCopy(
            FileFolderService fileFolderService,
            NodeRef sourceNodeRef,
            NodeRef sourceParentNodeRef,
            NodeRef destParentNodeRef,
            String name) throws Exception
    {
        NodeRef rootNodeRef = getRootNodeRef();

        String sourcePath = getPath();
        List<String> sourcePathElements = getDAVHelper().splitAllPaths(sourcePath);
        FileInfo sourceFileInfo = null;
        
        String destPath = getDestinationPath();
        List<String> destPathElements = getDAVHelper().splitAllPaths(destPath);
        FileInfo destFileInfo = null;
        
        NodeService nodeService = getNodeService();
        
        try
        {
            // get the node to move
            sourceFileInfo = fileFolderService.resolveNamePath(rootNodeRef, sourcePathElements);
            destFileInfo = fileFolderService.resolveNamePath(rootNodeRef, destPathElements);
        }
        catch (FileNotFoundException e)
        {
            if (sourceFileInfo == null)
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("Source node not found: " + sourcePath);
                }
                // nothing to move
                throw new WebDAVServerException(HttpServletResponse.SC_NOT_FOUND);
            }
        }

        checkNode(sourceFileInfo);

        if (destFileInfo != null && (isShuffleOperation(sourceFileInfo) || isVersioned(destFileInfo)))
        {
             copyOnlyContent(sourceNodeRef, destFileInfo, fileFolderService);
        }
        else
        // ALF-7079 fix, if source is working copy then it is just copied to destination
        if (nodeService.hasAspect(sourceNodeRef, ContentModel.ASPECT_WORKING_COPY))
        {
            // replace move with copy action for working copies
            fileFolderService.copy(sourceNodeRef, destParentNodeRef, name);
        }
        // ALF-7079 fix, if destination exists and is working copy then its content is updated with
        // source content and source is deleted
        else if (destFileInfo != null && nodeService.hasAspect(destFileInfo.getNodeRef(), ContentModel.ASPECT_WORKING_COPY))
        {
            // copy only content for working copy destination
            copyOnlyContent(sourceNodeRef, destFileInfo, fileFolderService);
        }
        else
        {
            if (sourceParentNodeRef.equals(destParentNodeRef)) 
            { 
               // It is rename method
               try
               {
                   fileFolderService.rename(sourceNodeRef, name);
               }
               catch (AccessDeniedException e)
               {
                   XMLWriter xml = createXMLWriter();

                   Attributes nullAttr = getDAVHelper().getNullAttributes();

                   xml.startElement(WebDAV.DAV_NS, WebDAV.XML_ERROR, WebDAV.XML_NS_ERROR, nullAttr);
                   // Output error
                   xml.write(DocumentHelper.createElement(WebDAV.XML_NS_CANNOT_MODIFY_PROTECTED_PROPERTY));

                   xml.endElement(WebDAV.DAV_NS, WebDAV.XML_ERROR, WebDAV.XML_NS_ERROR);
                   m_response.setStatus(HttpServletResponse.SC_CONFLICT);
                   return; 
               }
            } 
            else 
            { 
                // It is move operation 
                fileFolderService.moveFrom(sourceNodeRef, sourceParentNodeRef, destParentNodeRef, name); 
            } 
        }
    }
    
    private void copyOnlyContent(NodeRef sourceNodeRef, FileInfo destFileInfo, FileFolderService fileFolderService)
    {
    	ContentService contentService = getContentService();
        ContentReader reader = contentService.getReader(sourceNodeRef, ContentModel.PROP_CONTENT);
        ContentWriter contentWriter = contentService.getWriter(destFileInfo.getNodeRef(), ContentModel.PROP_CONTENT, true);
        contentWriter.putContent(reader);

        fileFolderService.delete(sourceNodeRef);
    }
}
