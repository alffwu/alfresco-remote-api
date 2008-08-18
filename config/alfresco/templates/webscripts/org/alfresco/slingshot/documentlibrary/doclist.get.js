<import resource="classpath:/alfresco/templates/webscripts/org/alfresco/slingshot/documentlibrary/action-sets.lib.js">
<import resource="classpath:/alfresco/templates/webscripts/org/alfresco/slingshot/documentlibrary/filters.lib.js">
<import resource="classpath:/alfresco/templates/webscripts/org/alfresco/slingshot/documentlibrary/parse-args.lib.js">

var THUMBNAIL_NAME = "doclib";

/**
 * Document List Component: doclist
 */
model.doclist = getDocList(args["filter"]);

/* Create collection of documents and folders in the given space */
function getDocList(filter)
{
   var items = new Array();
   var assets = new Array()
   
   // Is our thumbnail tpe registered?
   var haveThumbnails = thumbnailService.isThumbnailNameRegistered(THUMBNAIL_NAME);

   // Use helper function to get the arguments
   var parsedArgs = getParsedArgs();
   if (parsedArgs === null)
   {
      return;
   }

   // Try to find a filter query based on the passed-in arguments
   var allAssets, filterQuery;
   var filterParams = getFilterParams(filter, parsedArgs);
   if (filterParams === null)
   {
      // Default to all children of parentNode
      allAssets = parsedArgs.parentNode.children;
   }
   else if (filterParams == "node")
   {
      allAssets = [parsedArgs.rootNode];
   }
   else if (filterParams == "tag")
   {
      allAssets = parsedArgs.rootNode.childrenByTags(args["filterData"]);
   }
   else
   {
      // Run the query returned from the filter
      allAssets = search.luceneSearch(filterParams.query, "@cm:name", true);
      if (filterParams.limitResults)
      {
         /**
          * This isn't a true results trim (page-trimming is done below), as we haven't yet filtered by type.
          * However, it's useful for a quick slimming-down of the "recently..." queries.
          */
         allAssets = allAssets.slice(0, filterParams.limitResults);
      }
   }
   
   // Documents and/or folders?
   var showDocs = true;
   var showFolders = true;
   var type = url.templateArgs.type;
   if ((type !== null) && (type != ""))
   {
      showDocs = ((type == "all") || (type == "documents"));
      showFolders = ((type == "all") || (type == "folders"));
   }

   // Only interested in folders and/or documents depending on passed-in type
   folderAssets = new Array();
   documentAssets = new Array();
   for each(asset in allAssets)
   {
      if (showFolders && asset.isContainer)
      {
         folderAssets.push(asset);
      }
      else if (showDocs && asset.isDocument)
      {
         documentAssets.push(asset);
      }
   }
   assets = folderAssets.concat(documentAssets);
   
   // Make a note of totalRecords before trimming the assets array
   var totalRecords = assets.length;

   // Pagination
   var pageSize = args["size"] || assets.length;
   var pagePos = args["pos"] || "1";
   var startIndex = (pagePos - 1) * pageSize;
   assets = assets.slice(startIndex, pagePos * pageSize);
   
   var itemStatus, itemOwner, actionSet, thumbnail, createdBy, modifiedBy, activeWorkflows;
   
   // Locked/working copy status defines action set
   for each(asset in assets)
   {
      itemStatus = [];
      itemOwner = null;
      createdBy = null;
      modifiedBy = null;
      activeWorkflows = [];
      
      if (asset.isLocked)
      {
         itemStatus.push("locked");
         itemOwner = people.getPerson(asset.properties["cm:lockOwner"]);
      }
      if (asset.hasAspect("cm:workingcopy"))
      {
         itemStatus.push("workingCopy");
         itemOwner = people.getPerson(asset.properties["cm:workingCopyOwner"]);
      }
      // Is this user the item owner?
      if (itemOwner && (itemOwner.properties.userName == person.properties.userName))
      {
         itemStatus.push("lockedBySelf");
      }
      
      // Make sure we have a thumbnail
      if (haveThumbnails)
      {
         thumbnail = asset.getThumbnail(THUMBNAIL_NAME);
         if (thumbnail === null)
         {
            // No thumbnail, so queue creation
            asset.createThumbnail(THUMBNAIL_NAME, true);
         }
      }
      
      // Get users
      createdBy = people.getPerson(asset.properties["cm:creator"]);
      modifiedBy = people.getPerson(asset.properties["cm:modifier"]);
      
      // Get relevant actions set
      actionSet = getActionSet(asset,
      {
         itemStatus: itemStatus,
         itemOwner: itemOwner
      });
      
      // Resolve site, container and path
      var location =
      {
         site: null,
         container: null,
         path: null
      }
      var qnamePaths = asset.qnamePath.split("/");
      var displayPaths = asset.displayPath.split("/");
      if ((qnamePaths.length > 5) && (qnamePaths[2] == "st:sites"))
      {
         location = 
         {
            site: qnamePaths[3].substr(3),
            container: qnamePaths[4].substr(3),
            path: "/" + displayPaths.slice(5, displayPaths.length).join("/")
         }
      }
      
      // Part of an active workflow?
      for each (activeWorkflow in asset.activeWorkflows)
      {
         activeWorkflows.push(activeWorkflow.id);
      }
      
      items.push(
      {
         asset: asset,
         status: itemStatus,
         owner: itemOwner,
         createdBy: createdBy,
         modifiedBy: modifiedBy,
         actionSet: actionSet,
         tags: asset.tags,
         activeWorkflows: activeWorkflows,
         location: location
      });
   }

   return (
   {
      luceneQuery: filterParams.query,
      paging:
      {
         startIndex: startIndex,
         totalRecords: totalRecords
      },
      items: items
   });
}
