package server;

import java.awt.Point;
import java.io.File;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;

import common.*;
import entity.*;

public class MapDB {
	// Variables
	private static MapDB m_instance = null;
		
	/**
	 * Private constructor - prevent this object's creation 
	 */
	private MapDB() {}
		
	/**
	 * Returns a static instance of MapDB object
	 * @return MapDB
	 */
	public static synchronized MapDB getInstance() {
		if (m_instance == null) {
			m_instance = new MapDB();
		}

		return m_instance;
	}

	/**
	 * Searching for one or more maps according to search parameters provided by the client
	 * @param params - Contain {@link Action} type and the search parameters: 
	 * City name/Site name/Site or City description
	 * @return {@link Message} - Contain {@link ArrayList} of maps if found in database, else failure message
	 */
	public Message Search(ArrayList<Object> params) {
		// Variables
		ResultSet 				 rs 		  = null;
		ArrayList<Object> 		 data 	  	  = new ArrayList<Object>();
		HashMap<Integer, String> searchResult = new HashMap<Integer, String>();
					
		try {
			// Connect to DB
			SQLController.Connect();

			// Get sql query and search parameter
			String[] searchBy = getSearchSQL(params).split(":");
			
			// Execute sql query (located in index 1 of searchBy array), get results
			rs = SQLController.ExecuteQuery(searchBy[1], params);

			if(!rs.next())	// Search provided empty result
				throw new Exception("Could not find a map according to search parameters");

			rs.beforeFirst();	// Moves cursor to the start of the result set
			data.add(new Integer(0)); // Indicate action's success

			// Check if search was by city or site
			if (searchBy[0].equals("City")) {
				Integer routes = new Integer(0); // Counter for number of routes

				// Reads data 
				while (rs.next()) {
					// Insert into the HashMap the map's id and description
					searchResult.put(rs.getInt("mapID"), 
							rs.getString("mapDescription") + "," + rs.getInt("sitesCount"));
				}

				// Prepare parameters to to the query 
				if(params.size() == 1) params.add(params.get(0));

				// If result of the query was successful get the number of routes 
				Object result = ((Message)RouteDB.getInstance().getRoutesCount(params)).getData().get(1);
				if(!(result instanceof String)) 
					routes = (Integer)result;

				// Insert the results into the data array list
				data.add(searchResult); data.add(routes);				
			}
			else {	// Search was by Site
				// Reads data 
				while (rs.next()) {
					// Insert into the HashMap the map's id and description
					searchResult.put(rs.getInt("mapID"), 
							rs.getString("mapDescription") + "," + rs.getString("cityname"));
				}

				// Insert the results into the data array list
				data.add(searchResult);
			}

		} 
		catch (SQLException e) {
			data.add(new Integer(1));
			data.add("There was a problem with the SQL service.");
		}
		catch(Exception e) {
			data.add(new Integer(1));
			data.add(e.getMessage());
		}
		finally {
			// Disconnect DB
			SQLController.Disconnect(rs);
		}

		return new Message(Action.SEARCH, data);
	}

	/**
	 * Add new map to database
	 * @param params - Contain new map's details
	 * @return {@link Message} - Indicating success/failure with corresponding message 
	 */
	public Message AddNewMap(ArrayList<Object> params){
		// Variables
		ArrayList<Object> data = new ArrayList<Object>();

		try {
			// Check if the map to add is already in the database
			if(SQLController.DoesRecordExist("Maps", "mapname", "cityname", "description", 
					params.get(0), params.get(1), params.get(2)))
				throw new Exception("Map already exists.");

			// Prepare statement to insert new map
			String sql = "INSERT INTO Maps (`mapname`, `cityname`, `description`, `url`)" +
						 " VALUES (?, ?, ?, ?)";

			// Add the conversion of the map's image url to byte array to params object
			params.add(setImage(params.get(3).toString()));

			// Insert new map using private editMap method
			editMap(sql, params);

			// Create data to match the success pattern
			data.add(new Integer(0)); data.add(new String("Map was added successfully."));
		}
		catch (SQLException e) {
			data.add(new Integer(1)); data.add(new String("There was a problem with the SQL service."));
		}
		catch(Exception e) {
			data.add(new Integer(1)); data.add("Map was not added successfully");
		}
		finally {
			// Disconnect DB
			SQLController.Disconnect(null);
		}

		return new Message(null, data);
	}
	
	/**
	 * Add a new site to the requested map
	 * @param params - Contain new site's details and map and city name
	 * @return {@link Message} - Indicating success/failure with corresponding message
	 */
	public Message AddSiteToMap(ArrayList<Object> params){
		// Variables
		ArrayList<Object> data 		  = new ArrayList<Object>();
		int 			  changedRows = 0;
		String 			  sql 		  = "";

		try {
			// Check if a new map version is currently under management approval
			if(SQLController.DoesRecordExist("Inbox","content", "status", 
					"Approve " + params.get(0).toString() + " new version", "New"))
				throw new Exception("New version is under approval, cannot save new changes.");

			// Check if number of parameters is higher than 3 - indicating a new site is been added
			if (params.size() > 3) {
				ArrayList<Object> sitedetails = new ArrayList<Object>(params.subList(3, params.size()));

				// Adding name, location and is_active to the parameters for the WHERE clause
				sitedetails.add(params.get(3)); sitedetails.add(params.get(8));
				
				// Throwing exception in case the site's addition to Sites table was unsuccessful
				if (SiteDB.getInstance().AddSite(sitedetails) == 0) throw new Exception("Unable to add the site.");
			}

			// Prepare insert statement
			sql = "INSERT INTO BridgeMSC (`mapID`, `siteID`, `cityID`) "
					+ "VALUES ((SELECT id FROM Maps WHERE mapname = ?), "
					+ 		  "(SELECT id FROM Cities WHERE name = ?), "
					+ 		  "(SELECT id FROM Sites WHERE name = ?))";

			// Get the correct parameters for the map-site relation query
			params = new ArrayList<Object>(params.subList(0, 3));

			// Connect to DB
			SQLController.Connect();

			// Execute sql query, get number of changed rows
			changedRows = SQLController.ExecuteUpdate(sql, params);

			// Check if update was successful - result should be greater than zero
			if (changedRows == 0)
				 throw new Exception("Site was not added successfully.");

			data.add(new Integer(0));	// Add 0 to indicate success
			
			}
		catch (SQLException e) {
			data.add(new Integer(1));
			data.add("There was a problem with the SQL service.");
		}
		catch(Exception e) {
			data.add(new Integer(1));
			data.add(e.getMessage());
		}
		finally {
			// Disconnect DB
			SQLController.Disconnect(null);
		}

		return new Message(null, data);
	}
	
	/**
	 * Get maps list according to the requested city
	 * @param params - Contain city name
	 * @return {@link Message} - Contain {@link ArrayList} of type {@link Map} if retrieval succeeded, 
	 * else failure message
	 */
	public Message getMapsByCity(ArrayList<Object> params){
		// Variables
		ArrayList<Map>  maps 	 = new ArrayList<Map>();
		ArrayList<Site> sites;
		Message 		replyMsg = null;
		ResultSet 		rs 		 = null;

		try {
			// Connect to DB
			SQLController.Connect();

			String sql = "SELECT * FROM Maps WHERE cityname = ?"; // Prepare SELECT query
			
			// Execute sql query, get results
			rs = SQLController.ExecuteQuery(sql, params);

			// check if query succeeded
			if(!rs.next()) {
				throw new Exception("No map was found.");
			}

			rs.beforeFirst(); // Return cursor to the start of the first row
			
			// Reads data
			while (rs.next())
			{
				int id = rs.getInt("mapID");
				String mapname = rs.getString("mapname");
				String description = rs.getString("description");
				String cityname = rs.getString("cityname");
				byte[] image = rs.getBytes("url");

				// Clear lists of parameters for the next queries and add the desired parameter
				params.clear(); params.add(id);

				// Get map's list of sites
				Object mapSites = SiteDB.getInstance().getSitesbyMap(params);

				// Set the list of sites as null in case of a prbolem with the database/no sites for this map
				sites = ((Message)mapSites).getData().get(1) instanceof String ? null : 
					(ArrayList<Site>)((Message)mapSites).getData().get(1);

				// Add current instance of Map to the array list
				maps.add(new Map(id, mapname, description, cityname, sites, image));
			}

			replyMsg = new Message(null, new Integer(0), maps);	// Add content of the array list to the message
		}
		catch (SQLException e) {
			replyMsg = new Message(null, new Integer(1), e.getMessage());
		}
		catch(Exception e) {
			replyMsg = new Message(null, new Integer(1), e.getMessage());
		}
		finally {
			// Disconnect DB
			SQLController.Disconnect(rs);	
		}

		return replyMsg;
	}

	/**
	 * Get the amount of maps for a requested city
	 * @param params - Contains city name
	 * @return {@link Message} - Contains {@link ArrayList} of amount of {@link Map}s to a requested city,
	 * else failure message
	 */
	public Message getMapsCount(ArrayList<Object> params){
		// Variables
		HashMap<String,Integer> data      = new HashMap<String,Integer>();
		ResultSet 		        rs        = null;
		Message 		        replyMsg  = null;

		try {
			// Connect to DB
			SQLController.Connect();

			// Prepare SELECT query
			String sql = "SELECT cityName, COUNT(mapID) as 'numOfMaps' FROM `Maps` where cityname = '?'";

			// Execute sql query, get results
			rs = SQLController.ExecuteQuery(sql, params);

			// check if query succeeded
			if(!rs.next()) {
				throw new Exception("No map was found.");
			}

			rs.beforeFirst(); // Return cursor to the start of the first row

			// Reads data
			while (rs.next())
			{
				data.put(rs.getString("cityName"), rs.getInt("numOfMaps"));
			}

			replyMsg = new Message(null, data);	// Add content of the array list to the message
		}
		catch (SQLException e) {
			replyMsg = new Message(null, new Integer(1), e.getMessage());
		}
		catch(Exception e) {
			replyMsg = new Message(null, new Integer(1), e.getMessage());
		}
		finally {
			// Disconnect DB
			SQLController.Disconnect(rs);
		}

		return replyMsg;
	}

	/**
	 * Create a request for approval of a map's new version
	 * @param params - Contain map's name, the sender's user entity 
	 * @return {@link Message} - Indicating success/failure with corresponding message 
	 */
	public Message createNewVersionRequest(ArrayList<Object> params){
		// Variables
		ArrayList<Object> data  = new ArrayList<Object>();

		try {
			// Check if a new map version is currently under management approval
			if(SQLController.DoesRecordExist("Inbox","content", "status", 
						"Approve " + params.get(0).toString() + " new version", "New"))
				throw new Exception("New version is under approval, cannot create publish request.");

			// Prepare statement to insert new map
			String content = "Approve " + params.get(0).toString() + " new version";

			// Insert new Inbox message to managers with the approval request of map's new version
			Message msg = InboxDB.getInstance().AddInboxMessage(
					((User)params.get(1)).getUserName(), 
					((User)params.get(1)).getPermission().toString(),
					Permission.MANAGING_EDITOR.toString(),
					content,
					new String("New"),
					LocalDate.now());

			// Check if insertion was successful
			if((Integer)msg.getData().get(0) == 1) throw new Exception("Request for approval was not successful");

			// Create data to match the success pattern
			data.add(new Integer(0)); data.add(new String("Requset for approval submmited successfuly."));
		}
		catch (SQLException e) {
			data.add(new Integer(1)); data.add(new String("There was a problem with the SQL service."));
		}
		catch(Exception e) {
			data.add(new Integer(1)); data.add(e.getMessage());
		}
		finally {
			// Disconnect DB
			SQLController.Disconnect(null);
		}

		return new Message(null, data);
	}
	
	/**
	 * Update details in database of the current map's new version, set current version as active
	 * @param params - Contain the user entity of the manager, response - approve/decline, map name and id
	 * @return {@link Message} - Indicating success/failure with corresponding message
	 */
	public Message publishNewVersion(ArrayList<Object> params){
		// Variables
		ArrayList<Object> map_sities = new ArrayList<Object>();
		Message 		  replyMsg   = null;
		ResultSet 		  rs 	     = null;

		try {
			// Add is_active = false to get the new sites add to database and to the map to display them on this version
			// Get the id of sites that belong to the map using private getMapSitesID method
			params.add(0); 
			ArrayList<Object> input = new ArrayList<Object>(params.subList(3, params.size()));
			ArrayList<Object> sites = getMapSitesID(input);

			// Add response of management to publishing new version and the map id
			map_sities.add(params.get(1)); map_sities.add(params.get(3));

			// Enter each site into the array list for query
			for (Object site : sites)
				map_sities.add(site);

			// Delete sites related to the map which was approved, using map id given
			updateSitesOfMap(map_sities);

			map_sities.remove(1); // remove map id for the next method
			SiteDB.getInstance().approveNewSites(map_sities); // Approve new sites added for this new version

			// Add is_active = true to get the existing sites already in map to display their new details in map 
			input.remove(4); input.add(1); map_sities.clear(); map_sities.add(params.get(1));
			sites = getMapSitesID(input);

			// Enter each site into the array list for query
			for (Object site : sites)
				map_sities.add(site);

			// Approve editing of sites for this new version
			SiteDB.getInstance().approveEditedSites(map_sities);

			String content = "Got an update for map " + params.get(2).toString();
 			// Insert new Inbox message to clients letting them know there is a new version for the map
			Message msg = InboxDB.getInstance().AddInboxMessage(
					((User)params.get(0)).getUserName(), 
					((User)params.get(0)).getPermission().toString(),
					Permission.CLIENT.toString(),
					content,
					new String("New"),
					LocalDate.now());
		}
		catch (SQLException e) {
			replyMsg = new Message(null, new Integer(1), e.getMessage());
		}
		catch(Exception e) {
			replyMsg = new Message(null, new Integer(1), e.getMessage());
		}
		finally {
			// Disconnect DB
			SQLController.Disconnect(rs);	
		}

		return replyMsg;
	}

	/**
	 * Delete sites from map - set as to delete in the future when new version is approved or 
	 * delete sites when new version is approved
	 * @param params - Contain map's id where the sites should be deleted, and site id in case of future deletion
	 * @throws Exception 
	 */
	public void updateSitesOfMap(ArrayList<Object> params) throws Exception{
		// Variables
		ArrayList<Object> data 		  = new ArrayList<Object>();
		int 			  changedRows = 0;
		String 			  sql;
		
		try {
			// Build query according to the removal action - after new version's publishing or on delete
			// from site from map - management's action
			if(params.size() == 2) {
				sql = "UPDATE BridgeMSC SET to_delete = 1 " +
					  "WHERE mapID = ? AND siteID = ? AND is_active = ?";
				params.add(1);
			}
			// Check if the method was called as from the publishNewVersion method
			else if(params.size() > 2) {
				// If new version was approved - update is_active of new sites to true
				if(params.get(0).toString() == "Approve") {
					sql = "UPDATE BridgeMSC SET is_active = 1 " +
							 "siteID IN (";
					params = new ArrayList<Object>(params.subList(2, params.size())); // prepare parameters to query
				}
				else{
					sql = "DELETE FROM BridgeMSC WHERE mapID = ? AND " +
							"siteID IN (";
					params = new ArrayList<Object>(params.subList(1, params.size())); // prepare parameters to query
				}

				// Add question marks for the site's id
				for (int i = 2; i < params.size(); i++)
					sql += "?, ";

				sql = sql.substring(0, sql.length()-1) + ")";
			}
			else {
				// Prepare DELETE query to delete the sites from the map after map's publishing
				sql = "DELETE FROM BridgeMSC " +
					  "WHERE mapID = ? AND to_delete = ? AND is_active = ?";

				// Add parameters to complete the query
				params.add(1); params.add(1);	
			}

			// Execute sql query using private editMap method
			editMap(sql, params);
		}
		catch (SQLException e) {
			throw e;
		}
		catch(Exception e) {
			throw new Exception("Map's update after management response was unsuccessful");
		}
		finally {
			// Disconnect DB
			SQLController.Disconnect(null);
		}
	}

	/**
	 * Providing the sql search query according to the search criteria
	 * @param params - {@link ArrayList} of the search parameters
	 * @return String - the sql query with indicate if search was by site/city
	 */
	private String getSearchSQL(ArrayList<Object> params) {
		// Variables
		boolean city_name_exist   = !(params.get(0) == null);
		boolean site_name_exist   = !(params.get(1) == null);
		boolean description_exist = !(params.get(2) == null);
		String sql 				  = "";

		if (description_exist) {
			// Check whether the search was by site or city description
			if (SQLController.DoesRecordExist("Sites", "description", "%" + params.get(2).toString() + "%"))
				sql = "Site:" + "s.description LIKE ?";
			else
				sql = "City:" + "c.description LIKE ?";

			// Adding the description the special characters for the LIKE section in the query
			params.set(2, "%" + params.get(2).toString() + "%");
		}

		// Generate search sql according to the search parameters
		if (city_name_exist && site_name_exist && description_exist) {
			sql = "Site:" + "c.name = ? AND s.name = ? AND (c.description LIKE ? OR s.description LIKE ?)";

			params.add(params.get(2));	// Adding fourth parameter for the description's double input and

		}
		else if(city_name_exist && site_name_exist) {
			sql = "Site:" + "c.name = ? AND s.name = ?";
		}
		else if(city_name_exist && description_exist) {
			sql = sql.split(":")[0] + ":c.name = ? AND " + sql.split(":")[1];
		}
		else if(site_name_exist && description_exist) {
			sql = sql.split(":")[0] + ":s.name = ? AND " + sql.split(":")[1];
		}
		else if(city_name_exist) {
			sql = "City:" + "c.name = ?";
		}
		else if(site_name_exist) {
			sql = "Site:" + "s.name = ?";
		}

		// Remove from params null entries
		params.removeIf(filter -> filter == null);
		// Prepare sql statement according to the search criteria
		if(sql.split(":")[0].equals("City"))
			sql = "City:SELECT search.mapID as \"mapID\", m.description as \"mapDescription\", " +
					"	  COUNT(s.siteID) as \"sitesCount\" " + 
					"	  FROM (SELECT b.mapID as \"mapID\", b.siteID as \"siteID\" " + 
					"			FROM BridgeMSC b " + 
					"			WHERE b.mapID IN (SELECT b.mapID as \"mapID\" " + 
					"						      FROM Sites s, Maps m, BridgeMSC b, Cities c " + 
					"						      WHERE m.mapID = b.mapID AND s.siteID = b.siteID " +
					" AND c.id = b.cityID AND " + sql.split(":")[1] + ")) as search, Sites s, Maps m " + 
					"	  WHERE search.mapID = m.mapID AND search.siteID = s.siteID " + 
					"	  GROUP BY search.mapID";
		else
			sql = "Site:SELECT b.mapID as \"mapID\", s.cityname as \"cityname\", " +
								"m.description as \"mapDescription\" " + 
								"FROM Sites s, Maps m, BridgeMSC b, Cities c " +
								"WHERE m.mapID = b.mapID AND s.siteID = b.siteID AND c.id = b.cityID " + 
								"AND " + sql.split(":")[1];

		return sql;
	}

	/**
	 * Private method to get the id of the sites of a specific map
	 * @param params - {@link ArrayList} contain map id, is_active type - current displayed or for future display
	 * @return {@link ArrayList} - an {@link ArrayList} of type int representing the map's sites' id
	 */
	private ArrayList<Object> getMapSitesID(ArrayList<Object> params){
		// Variables
		ArrayList<Object> sites = new ArrayList<Object>();
		ResultSet	   rs	 	= null;

		try {
			// Connect to DB
			SQLController.Connect();

			// Prepare SELECT query
			String sql = "SELECT siteID FROM BridgeMSC WHERE mapID = ? AND is_active = ?";

			// Execute sql query, get results
			rs = SQLController.ExecuteQuery(sql, params);

			// check if query succeeded
			if(!rs.next()) {
				return null;
			}

			rs.beforeFirst(); // Return cursor to the start of the first row
			
			// Read data
			while (rs.next())
				sites.add(new Integer(rs.getInt("siteiD")));
		}
		catch (SQLException e) {
			return null;
		}
		catch(Exception e) {
			return null;
		}
		finally {
			// Disconnect DB
			SQLController.Disconnect(rs);	
		}

		return sites;
	}

	/**
	 * Generic query for UPDATE queries 
	 * @param sql - the UPDATE query 
	 * @param params - {@link ArrayList} of parameters to complete the requested UPDATE query
	 * @throws SQLException, Exception
	 */
	private void editMap(String sql, ArrayList<Object> params) throws SQLException, Exception {
		try {
			// Connect to DB
			SQLController.Connect();

			// Execute sql query, get number of rows affected
			int changedRows = SQLController.ExecuteUpdate(sql, params);

			// Check if update was successful - result should be greater than zero
			if (changedRows == 0)
				 throw new Exception("Operation on sites was unsuccessful.");
			}
		catch (SQLException e) {
			throw e;
		}
		catch(Exception e) {
			throw e;
		}
		finally {
			// Disconnect DB
			SQLController.Disconnect(null);
		}
	}

	/**
	 * Private method to turn image to byte array to be saved in database
	 * @param path - image's path
	 * @return byte array representing image
	 */
	private byte[] setImage(String path) {
		if (path == null)
			return null;
		else {
			// Get real path of image
			path = path.substring(path.indexOf("file:/") + 6, path.length());
			File file = new File(path); // try to open the file in path
			try {
					return Files.readAllBytes(file.toPath()); // reads content of image as byte
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		return null;
	}
}