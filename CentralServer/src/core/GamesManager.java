package core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import parser.ChessParser;
import parser.IncorrectFENException;

/**
 * Handle all the accesses to the Games and Moves tables in the SQLite database.
 * @author Clement Gautrais
 */
public class GamesManager extends DatabaseManager {
	private static final String FIRST_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1";
	private static final String GAMES = "games";
	private static final String MOVES = "moves";
	private static final String GAME_ID = "id";
	private static final String GAME_FEN = "fen";
	private static final String GAME_SAN = "san";
	private static final String MOVE_GAME = "game";
	private static final String MOVE_RESOURCE = "resource";
	private static final String MOVE_NUMBER = "num_move"; // The number of the move in the game
	private static final String MOVE_TRUST = "move_trust";
	
	/**
	 * Remove all traces of the game id_game.
	 * @param gameId The id of the game to remove.
	 * @return True if the game has been removed, false otherwise.
	 */
	@SuppressWarnings("resource")
	public static boolean removeGame(int gameId) {
		Connection dbConnect = getConnection();
		String queryMoves = "DELETE FROM "+MOVES+" WHERE "+MOVE_GAME+" = ?";
		try {
			PreparedStatement statement = dbConnect.prepareStatement(queryMoves);
			statement.setInt(1, gameId);
			statement.executeUpdate();
			statement.close();
		} catch(SQLException e) {
			System.err.println("removeGame: "+e.getMessage());
			return false;
		}
		String queryGames = "DELETE FROM "+GAMES+" WHERE "+GAME_ID+" = ?";
		try {
			PreparedStatement statement = dbConnect.prepareStatement(queryGames);
			statement.setInt(1, gameId);
			if(statement.executeUpdate()!=1) {
				statement.close();
				dbConnect.close();
				return false;
			}
			statement.close();
			dbConnect.close();
		} catch(SQLException e) {
			System.err.println("removeGame: "+e.getMessage());
			fireQueryError(e);
			return false;
		}
		return true;
	}
	
	/**
	 * Create a new game and return the id created.
	 * @return The id of the new game, -1 if an error occurred.
	 */
	public static int addNewGame(boolean san) {
		int id = generateGameId();
		try {
			Connection dbConnect = getConnection();
			String query = "INSERT INTO "+GAMES+" ("+GAME_ID+", "+GAME_FEN+", "+GAME_SAN+") VALUES(?, ?, ?)";
			PreparedStatement statement = dbConnect.prepareStatement(query);
			statement.setInt(1, id);
			statement.setString(2, FIRST_FEN);
			statement.setBoolean(3, san);
			if(statement.executeUpdate()!=1) {
				statement.close();
				dbConnect.close();
				return -1;
			}
			statement.close();
			dbConnect.close();
		} catch(SQLException e) {
			System.err.println("addNewGame: "+e.getMessage());
			fireQueryError(e);
			return -1;
		}
		return id;
	}
	
	/**
	 * Update a game.
	 * @param gameId The id of the game to update.
	 * @param fen The new fen.
	 * @return True if the update succeed. It can fail if the game doesn't exist in the database.
	 */
	@SuppressWarnings("resource")
	public static boolean updateGame(int gameId, String fen) {
		Connection dbConnect = getConnection();
		String query = "UPDATE "+GAMES+" SET "+GAME_FEN+" = ? WHERE "+GAME_ID+" = ?";
		try {
			PreparedStatement statement = dbConnect.prepareStatement(query);
			statement.setString(1, fen);
			statement.setInt(2, gameId);
			if(statement.executeUpdate()!=1) {
				dbConnect.close();
				statement.close();
				return false;
			}
			statement.close();
			dbConnect.close();
			return true;
		} catch(SQLException e) {
			System.err.println("updateGame: "+e.getMessage());
			fireQueryError(e);
			return false;
		}
	}
	
	/**
	 * Return the number of moves in the game gameId.
	 * @param gameId The id of the game concerned.
	 * @return The number of moves, -1 if an error occurred.
	 */
	@SuppressWarnings("resource")
	public static int getNumberOfMoves(int gameId) {
		Connection dbConnect = getConnection();
		String query = "SELECT MAX("+MOVE_NUMBER+") AS max FROM "+MOVES+" WHERE "+MOVE_GAME+" = ?";
		try {
			PreparedStatement statement = dbConnect.prepareStatement(query);
			statement.setInt(1, gameId);
			ResultSet set = statement.executeQuery();
			int nbMoves = -1;
			if(set.next()) {
				nbMoves = set.getInt("max");
			}
			set.close();
			statement.close();
			dbConnect.close();
			return nbMoves;
		} catch(SQLException e) {
			System.err.println("getNumberOfMoves: "+e.getMessage());
			fireQueryError(e);
			return -1;
		}
	}
	
	/**
	 * Get the color of the last FEN play.
	 * This means getting the player color.
	 * @param gameId The id of the game concerned.
	 * @return The computer color for the game concerned, null if the game doesn't exist.
	 * @throws IncorrectFENException If the FEN is incorrect.
	 */
	@SuppressWarnings("resource")
	public static PlayerColor getColor(int gameId) throws IncorrectFENException {
		Connection dbConnect = getConnection();
		String query = "SELECT "+GAME_FEN+" FROM "+GAMES+" WHERE "+GAME_ID+" = ?";
		try {
			PreparedStatement statement = dbConnect.prepareStatement(query);
			statement.setInt(1, gameId);
			ResultSet set = statement.executeQuery();
			PlayerColor color = null;
			if(set.next()) {
				String fen = set.getString(GAME_FEN);
				color = ChessParser.getColor(fen);
			}
			set.close();
			statement.close();
			dbConnect.close();
			return color;
		} catch(SQLException e) {
			System.err.println("getColor: "+e.getMessage());
			fireQueryError(e);
		}
		return null;
	}
	
	/**
	 * Generate randomly an id that isn't used.
	 * @return The id.
	 */
	private static int generateGameId() {
		int id;
		do {
			id = 1 + (int)(Math.random()*500000);
		} while(exist(id));
		return id;
	}
	
	/**
	 * Check if a game exist in the database.
	 * @param gameId The game id.
	 * @return True if the game exists.
	 */
	@SuppressWarnings("resource")
	public static boolean exist(int gameId) {
		Connection dbConnect = getConnection();
		String query = "SELECT "+GAME_ID+" FROM "+GAMES+" WHERE "+GAME_ID+" = ?";
		try {
			PreparedStatement statement = dbConnect.prepareStatement(query);
			statement.setInt(1, gameId);
			ResultSet set = statement.executeQuery();
			dbConnect.close();
			boolean exist = set.next();
			set.close();
			statement.close();
			return exist;
		} catch(SQLException e) {
			System.err.println("exist: "+e.getMessage());
			fireQueryError(e);
			return true;
		}
	}
	
	/**
	 * Add a new move for reward.
	 * @param gameId The id of the game.
	 * @param resourcesConfidence The confidence of each resources in the move.
	 * @param moveNumber The move number.
	 * @return True if added, false otherwise.
	 */
	@SuppressWarnings("resource")
	public static boolean addMove(int gameId, Map<Integer, Double> resourcesConfidence, int moveNumber) {
		Connection dbConnect = getConnection();
		String query = "INSERT INTO "+MOVES+" ("+MOVE_GAME+", "+MOVE_RESOURCE+", "+MOVE_NUMBER+", "+MOVE_TRUST+") VALUES(?, ?, ?, ?)";
		try {
			PreparedStatement statement = dbConnect.prepareStatement(query);
			for(Entry<Integer, Double> resourceConfidence: resourcesConfidence.entrySet()) {
				statement.setInt(1, gameId);
				statement.setInt(2, resourceConfidence.getKey());
				statement.setInt(3, moveNumber);
				statement.setDouble(4, resourceConfidence.getValue());
				statement.addBatch();
			}
			int[] results = statement.executeBatch();
			statement.close();
			for(int i=0; i<results.length; i++) {
				if(results[i]!=1) {
					dbConnect.close();
					return false;
				}
			}
			dbConnect.close();
			return true;
		} catch(SQLException e) {
			System.err.println("addMoves: "+e.getMessage());
			fireQueryError(e);
			return false;
		}
	}
	
	/**
	 * Get the involvement in the game for each resources.
	 * Return a map with the resource as key and the total of trust in the moves.
	 * It only add the trust in the move suggestions that were finally played.
	 * @param gameId The id of the game to scan.
	 * @return The map or null if an error occurred.
	 */
	@SuppressWarnings("resource")
	public static Map<Integer, Double> getResourceInvolvements(int gameId) {
		Map<Integer, Double> resourceInvolvements = new HashMap<Integer, Double>();
		int nbTotalMoves = getNumberOfMoves(gameId);
		if(nbTotalMoves<=0) {
			return resourceInvolvements;
		}
		if(exist(gameId)) {
			Connection dbConnect = getConnection();
			String query = "SELECT TOTAL("+MOVE_TRUST+") AS totalTrust, "+MOVE_RESOURCE+" FROM "+MOVES+" WHERE "+MOVE_GAME+"= ? GROUP BY "+MOVE_RESOURCE;
			try {
				PreparedStatement statement = dbConnect.prepareStatement(query);
				statement.setInt(1, gameId);
				ResultSet set = statement.executeQuery();
				while(set.next()) {
					resourceInvolvements.put(set.getInt(MOVE_RESOURCE), set.getDouble("totalTrust"));
				}
				set.close();
				statement.close();
				dbConnect.close();
				return resourceInvolvements;
			} catch(SQLException e) {
				System.err.println("getResourceInvolvements: "+e.getMessage());
				fireQueryError(e);
			}
		}
		return null;
	}
	
	/**
	 * Check if a game require SAN moves to be send or LAN.
	 * @param gameId The game id.
	 * @return True if the server must return SAN for this game, null if the game doesn't exist.
	 */
	@SuppressWarnings("resource")
	public static Boolean isSAN(int gameId) {
		Connection dbConnect = getConnection();
		String query = "SELECT "+GAME_SAN+" FROM "+GAMES+" WHERE "+GAME_ID+" = ?";
		try {
			PreparedStatement statement = dbConnect.prepareStatement(query);
			statement.setInt(1, gameId);
			ResultSet set = statement.executeQuery();
			Boolean san = null;
			if(set.next()) {
				san = set.getBoolean(GAME_SAN);
			}
			set.close();
			statement.close();
			dbConnect.close();
			return san;
		} catch(SQLException e) {
			System.err.println("isSAN: "+e.getMessage());
			fireQueryError(e);
		}
		return null;
	}
}