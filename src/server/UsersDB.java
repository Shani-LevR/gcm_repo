package server;

import java.sql.ResultSet;

import java.sql.SQLException;
import java.util.ArrayList;

import common.*;
import entity.*;

public class UsersDB {
	// Variables
	private static UsersDB m_instance = null;
	
	/**
	 * Private constructor - prevent this object's creation 
	 */
	private UsersDB() {}
	
	/**
	 * Returns a static instance of UsersDB object
	 * @return UserDB
	 */
	public static synchronized UsersDB getInstance() {
		if (m_instance == null) {
			m_instance = new UsersDB();
		}
		
		return m_instance;
	}

	/**
	 * Add a new {@link User} to database
	 * @param params - Contain {@link Action} type and details of the new user
	 * @return {@link Message} - Indicating success/failure with corresponding message
	 */
	public Message AddUser(ArrayList<Object> params) {
		// Variables
		ArrayList<Object> data = new ArrayList<Object>();
		// Get the users's password
		String hash="";
		String password = params.get(3).toString();
		// Create a unique and random salt to the new user
		byte[] salt = common.Jhash.Hash.randomSalt(24);
		try { 
			hash = common.Jhash.Hash.createHash(password, salt);
		}
		catch (Exception e)	{
			data.add(new Integer(1));
			data.add("Producing hashed password encountered a problem.");
		}
		//Add hashed password and user's unique salt to params.
		params.set(3, hash);
		params.set(4, salt);

		try {
			// Connect to DB
			SQLController.Connect();

			// Prepare statement to insert new user
			String sql = "INSERT INTO Clients (`firstname`, `lastname`, `username`, `password`, `salt`, `email`, " +
						"`permission`, `telephone`, `cardnumber`, `id`, `expirydate`)" +
						" VALUES (?, ?, ?, ?, ? ,? ,?, ?, ?, ?, ?)";


			// Execute sql query, get number of changed rows
			int changedRows = SQLController.ExecuteUpdate(sql, params);

			// Check if insert was successful - result should be greater than zero
			if (changedRows == 0) {
				 throw new Exception("User was not added successfully.");
			}

			// Add 0 to indicate success
			data.add(new Integer(0));

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

		return (new Message(Action.REGISTER, data));
	}

	/**
	 * Get {@link User} according to the user's name and password provided by the client
	 * @param params - Contain {@link Action} type and the user's name of the requested user
	 * @return {@link Message} - Indicating success/failure with corresponding message
	 */
	public Message getUser(ArrayList<Object> params) {
		// Variables
		ArrayList<Object> data = new ArrayList<Object>();
		ResultSet         rs   = null;

		// Save password given by the user
		String password = params.get(1).toString();
		params.remove(1);

		try {
			// Connect to DB
			SQLController.Connect();

			// Prepare statement to get current client's details
			String sql = "SELECT * FROM Clients WHERE username = ?";

			// Execute sql query, get results
			rs = SQLController.ExecuteQuery(sql, params);

			// check if query succeeded
			if(!rs.next()) {
				throw new Exception("Username does not exist.");
			}
			else {
				byte[] salt = rs.getBytes(5);
				// Compare between password given by user to password hashed in the database.
				if(!(common.Jhash.Hash.validatePassword(password, new String(rs.getBytes(4)), salt)))
				{
					data.add(new Integer(1));
					data.add("Password does not match username.");
				}
				else
				{
				data.add(new Integer(0)); // set query result as success
				rs.beforeFirst();	// Moves cursor to the start of the result set	

				// Reads data
				while (rs.next()) {
					User currUser;
					Permission per = Permission.valueOf(rs.getString("permission").toUpperCase());
					if (per == Permission.CLIENT) {
						currUser = new Client(
								rs.getString("firstname"),
								rs.getString("lastname"),
								rs.getString("password"),
								rs.getBytes("salt"),
								rs.getString("username"),
								rs.getString("email"),
								Permission.valueOf(rs.getString("permission").toUpperCase()),
								rs.getLong("telephone"),
								rs.getLong("cardnumber"),
								rs.getLong("id"),
								rs.getDate("expirydate").toLocalDate());
					}
					else {
						currUser = new Employee(
								rs.getString("firstname"),
								rs.getString("lastname"),
								rs.getString("password"),
								rs.getBytes("salt"),
								rs.getString("username"),
								rs.getString("role"),
								rs.getString("email"),
								Permission.valueOf(rs.getString("permission").toUpperCase()),
								rs.getInt("id"));
					}

					data.add(currUser);
				}
			}
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

		return new Message(null, data);
	}

	/**
	 * Edit a specific user's details 
	 * @param params - Contain {@link Action} type, user name of the requested user and the new details
	 * @return {@link Message} - Indicating success/failure with corresponding message
	 */
	public Message EditUser(ArrayList<Object> params) {
		// Variables
		ArrayList<Object> data = new ArrayList<Object>();

		try {
			// Connect to DB
			SQLController.Connect();

			// Prepare statement to insert new user
			String sql = "UPDATE Clients SET firstname = ?, lastname = ?, username = ?, password = ?," +
						" email = ?, permission = ?, telephone = ?, cardnumber = ?, id = ?, expirydate = ?" +
						" WHERE username = ?";


			// Execute sql query, get number of changed rows
			int changedRows = SQLController.ExecuteUpdate(sql, params);

			// Check if update was successful - result should be greater than zero
			if (changedRows == 0) {
				 throw new Exception("User was not added successfully.");
			}

			// Add 0 to indicate success
			data.add(new Integer(0));

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

		return (new Message(Action.EDIT_USER_DETAILS, data));
	}
}