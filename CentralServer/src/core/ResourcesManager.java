package core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handle all the accesses to the SQLite database for the resources.
 * @author Paul Chaignon
 */
public class ResourcesManager extends DatabaseManager {
	private static final String RESOURCES = "resources";
	private static final String RESOURCE_ID = "id";
	private static final String RESOURCE_URI = "uri";
	private static final String RESOURCE_NAME = "name";
	private static final String RESOURCE_TRUST = "trust";
	private static final String RESOURCE_TYPE = "type";
	private static final String RESOURCE_ACTIVE = "active";
	
	/**
	 * @param active If true then this method will only return the active resources.
	 * @return All resources from the database.
	 */
	public static Set<Resource> getResources(boolean active) {
		Set<Resource> resources = new HashSet<Resource>();
		Connection dbConnect = getConnection();
		String query = "SELECT * FROM "+RESOURCES;
		if(active) {
			query += " WHERE active = 1";
		}
		try {
			PreparedStatement statement = dbConnect.prepareStatement(query);
			ResultSet results = statement.executeQuery();
			while(results.next()) {
				Resource resource;
				if(results.getInt(RESOURCE_TYPE)==Resource.OPENINGS_DATABASE) {
					resource = new OpeningsDatabase(results.getString(RESOURCE_URI), results.getString(RESOURCE_NAME), results.getInt(RESOURCE_TRUST), results.getBoolean(RESOURCE_ACTIVE));
				} else if(results.getInt(RESOURCE_TYPE)==Resource.ENDINGS_DATABASE) {
					resource = new EndingsDatabase(results.getString(RESOURCE_URI), results.getString(RESOURCE_NAME), results.getInt(RESOURCE_TRUST), results.getBoolean(RESOURCE_ACTIVE));
				} else {
					resource = new Bot(results.getString(RESOURCE_URI), results.getString(RESOURCE_NAME), results.getInt(RESOURCE_TRUST), results.getBoolean(RESOURCE_ACTIVE));
				}
				resource.setId(results.getInt(RESOURCE_ID));
				resources.add(resource);
			}
			dbConnect.close();
		} catch(SQLException e) {
			System.err.println("getResources: "+e.getMessage());
		}
		
		return resources;
	}
	
	/**
	 * Add a resource to the database and set the resource id with the one used in the sql table
	 * @param resource The resource to add.
	 * @return True if the operation succeed, false otherwise.
	 */
	public static boolean addResource(Resource resource) {
		Connection dbConnect = getConnection();
		String query = "INSERT INTO "+RESOURCES+"("+RESOURCE_TYPE+", "+RESOURCE_NAME+", "+RESOURCE_URI+", "+RESOURCE_TRUST+", "+RESOURCE_ACTIVE+") VALUES(?, ?, ?, ?, 1)";
		try {
			PreparedStatement statement = dbConnect.prepareStatement(query);
			statement.setQueryTimeout(20);
			int type = Resource.BOT;
			if(resource.getClass()==OpeningsDatabase.class) {
				type = Resource.OPENINGS_DATABASE;
			} else if(resource.getClass()==EndingsDatabase.class) {
				type = Resource.ENDINGS_DATABASE;
			}
			statement.setInt(1, type);
			statement.setString(2, resource.getName());
			statement.setString(3, resource.getURI());
			statement.setInt(4, resource.getTrust());
			if(statement.executeUpdate()!=1) {
				dbConnect.close();
				return false;
			} else {
				String queryLastId = "SELECT last_insert_rowid() AS last_id";
				statement = dbConnect.prepareStatement(queryLastId);
				ResultSet res = statement.executeQuery();
				res.next();
				resource.setId(res.getInt("last_id"));
				return true;
			}
		} catch(SQLException e) {
			System.err.println("addResource: "+e.getMessage());
		}
		return false;
	}
	
	/**
	 * Remove a resource from the database.
	 * @param resource The resource to remove.
	 * @return True if the operation succeed, false otherwise.
	 */
	public static boolean removeResource(Resource resource) {
		Connection dbConnect = getConnection();
		String query = "DELETE FROM "+RESOURCES+" WHERE "+RESOURCE_ID+" = ?";
		try {
			PreparedStatement statement = dbConnect.prepareStatement(query);
			statement.setInt(1, resource.getId());
			if(statement.executeUpdate()!=1) {
				dbConnect.close();
				return false;
			}
			dbConnect.close();
			return true;
		} catch(SQLException e) {
			System.err.println("removeResource: "+e.getMessage());
		}
		return false;
	}
	
	/**
	 * Remove resources from the database.
	 * @param resources The resources to remove.
	 * @return The resources that weren't removed.
	 */
	public static Set<Resource> removeResources(Set<Resource> resources) {
		List<Resource> resourcesToRemove = new ArrayList<Resource>();
		resourcesToRemove.addAll(resources);
		Set<Resource> notRemoved = new HashSet<Resource>();
		Connection dbConnect = getConnection();
		String query = "DELETE FROM "+RESOURCES+" WHERE "+RESOURCE_ID+" = ?";
		try {
			PreparedStatement statement = dbConnect.prepareStatement(query);
			for(Resource resource: resourcesToRemove) {
				statement.setInt(1, resource.getId());
				statement.addBatch();
			}
			int[] results = statement.executeBatch();
			for(int i=0 ; i<results.length ; i++) {
				if(results[i]==0) {
					notRemoved.add(resourcesToRemove.get(i));
				}
			}
			dbConnect.close();
		} catch(SQLException e) {
			System.err.println("removeResources: "+e.getMessage());
		}
		return notRemoved;
	}
	
	/**
	 * Update a resource in the database.
	 * All fields except the URI can be updated.
	 * @param resource The resource to update.
	 * @return True if the update succeed, false otherwise.
	 */
	public static boolean updateResource(Resource resource) {
		Connection dbConnect = getConnection();
		String query = "UPDATE "+RESOURCES+" SET "+RESOURCE_NAME+" = ?, "+RESOURCE_TRUST+" = ?, "+RESOURCE_TYPE+" = ?, "+RESOURCE_ACTIVE+" = ? WHERE "+RESOURCE_ID+" = ?";
		try {
			PreparedStatement statement = dbConnect.prepareStatement(query);
			statement.setString(1, resource.getName());
			statement.setInt(2, resource.getTrust());
			int type = Resource.BOT;
			if(resource.getClass()==OpeningsDatabase.class) {
				type = Resource.OPENINGS_DATABASE;
			} else if(resource.getClass()==EndingsDatabase.class) {
				type = Resource.ENDINGS_DATABASE;
			}
			statement.setInt(3, type);
			statement.setBoolean(4, resource.isActive());
			statement.setInt(5, resource.getId());
			if(statement.executeUpdate()!=1) {
				dbConnect.close();
				return false;
			}
			dbConnect.close();
			return true;
		} catch(SQLException e) {
			System.err.println("updateResource: "+e.getMessage());
		}
		return false;
	}
	
	/**
	 * Update the trust parameter of resources.
	 * @param resourcesInvolvement A map with the id of the resources as key and the value that need to be add to the trust of the resource.
	 * @param gameResult The result of the game: -1 for lose, 1 for win, 0 for draw.
	 * @return The id of the resources that weren't updated.
	 */
	public static Set<Integer> updateResourcesTrust(Map<Integer, Double> resourcesInvolvement, int gameResult) {
		Set<Integer> notUpdated = new HashSet<Integer>();
		List<Integer> resourcesToUpdate = new ArrayList<Integer>();
		resourcesToUpdate.addAll(resourcesInvolvement.keySet());
		Connection dbConnect = getConnection();
		String query = "UPDATE "+RESOURCES+" SET "+RESOURCE_TRUST+" += ? WHERE "+RESOURCE_ID+" = ?";
		try {
			PreparedStatement statement = dbConnect.prepareStatement(query);
			for(int resourceId: resourcesToUpdate) {
				double reward = gameResult*resourcesInvolvement.get(resourceId);
				statement.setInt(1, (int)reward);
				statement.setInt(2, resourceId);
				statement.addBatch();
			}
			int[] results = statement.executeBatch();
			for(int i=0 ; i<results.length ; i++) {
				if(results[i]==0) {
					notUpdated.add(resourcesToUpdate.get(i));
				}
			}
			dbConnect.close();
		} catch(SQLException e) {
			System.err.println("updateResourcesTrust: "+e.getMessage());
		}
		return notUpdated;
	}
	
	/**
	 * Update the active parameter of resources.
	 * @param resources The resources whose active parameter is to update.
	 * @return The resources that weren't updated.
	 */
	public static Set<Resource> updateResourcesActive(Set<Resource> resources) {
		Set<Resource> notUpdated = new HashSet<Resource>();
		List<Resource> resourcesToUpdate = new ArrayList<Resource>();
		resourcesToUpdate.addAll(resources);
		Connection dbConnect = getConnection();
		String query = "UPDATE "+RESOURCES+" SET "+RESOURCE_ACTIVE+" = ? WHERE "+RESOURCE_ID+" = ?";
		try {
			PreparedStatement statement = dbConnect.prepareStatement(query);
			for(Resource resource: resourcesToUpdate) {
				statement.setBoolean(1, resource.isActive());
				statement.setInt(2, resource.getId());
				statement.addBatch();
			}
			int[] results = statement.executeBatch();
			for(int i=0 ; i<results.length ; i++) {
				if(results[i]==0) {
					notUpdated.add(resourcesToUpdate.get(i));
				}
			}
			dbConnect.close();
		} catch(SQLException e) {
			System.err.println("updateResourcesActive: "+e.getMessage());
		}
		return notUpdated;
	}
}