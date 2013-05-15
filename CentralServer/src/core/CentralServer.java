package core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manage the list of resources.
 * @author Benoit Travers
 * @author Paul Chaignon
 * @author Clement Gautrais
 */
public class CentralServer {
	private Set<Resource> resources;
	private static final String version = "1.0";
	private static final int REWARD_VALUE = 20;

	/**
	 * Constructor
	 */
	public CentralServer() {
		this.resources = new HashSet<Resource>();
		this.restoreResources();
		this.checkVersion();
	}

	/**
	 * Restore the list of resources from the database file.
	 */
	private void restoreResources() {
		this.resources = ResourcesManager.getResources(true);
	}

	/**
	 * Check resources version.
	 */
	private void checkVersion() {
		String centralserveur_version = CentralServer.version.substring(0, CentralServer.version.indexOf('.'));
		Set<Resource> incompatibleResources = new HashSet<Resource>();

		for(Resource resource: this.resources) {
			resource.checkVersion();


			if(!resource.isConnected()) {
				incompatibleResources.add(resource);
			} else {
				String resource_version = resource.getVersion().substring(0, resource.getVersion().indexOf("."));
				if(!centralserveur_version.equals(resource_version)) {
					incompatibleResources.add(resource);
				}
			}
		}

		// TODO Save incompatible resources.

		for(Resource resource: incompatibleResources) {
			this.resources.remove(resource);
		}
	}

	/**
	 * Save a resource in the corresponding table.
	 */
	public void saveResourcesTrust(Set<Resource> resources) {
		ResourcesManager.updateResourcesTrust(resources);	
	}

	/**
	 * Get the suggestion of move from the resources and compute the best answer.
	 * @param fen The FEN.
	 * @param gameId The game id.
	 * @return The best move or null if no suggestion.
	 */
	public String getBestMove(String fen, int gameId) {
		this.updateResources(fen);
		// This map contains all the moves and the scores associated except the ending moves.
		Map<String, Double> scores = new HashMap<String, Double>();
		// This map contains the ending moves with their scores.
		Map<String, Double> ends = new HashMap<String, Double>();

		for(Resource resource : this.resources) {
			for(MoveSuggestion move : resource.getMoveSuggestions()) {
				if(move.getClass()==EndingSuggestion.class) {
					ends.put(move.getMove(), move.getScore());	
				} else {
					if(scores.containsKey(move.getMove())) {
						double newScore = scores.get(move.getMove()) + resource.getTrust()*move.getScore();
						scores.put(move.getMove(), newScore);
					} else {
						scores.put(move.getMove(), resource.getTrust()*move.getScore());
					}
				}
			}
		}
		
		String bestMove = this.bestMove(scores, ends);
		
		if(gameId>0) {
			GamesManager.addMoves(gameId, getMoveResourcesConfidence(bestMove), GamesManager.getNumberOfMoves(gameId)+1);
		}
		
		StatsManager.updateStatistics(getSuggestions());

		if(bestMove==null) {
			return null;
		}
		System.out.println(bestMove); // Call listener instead.
		return bestMove;
	}

	/**
	 * Get the best move by comparing the scores among all moves suggested.
	 * @param moves The map containing all the moves and the scores associated.
	 * @param ends The map containing all the ending moves and their scores.
	 * @return The best move among all moves or null if no suggestion.
	 */
	private String bestMove(Map<String, Double> moves, Map<String, Double> ends) {
		String move = null;
		if(ends.size()>0) {
			double min = Double.MAX_VALUE;
			for(Map.Entry<String, Double> entry: ends.entrySet()) {
				if(entry.getValue() < min) {
					min = entry.getValue();
					move = entry.getKey();
				}
			}
			if(min > 0) {
				// Use an end suggestion only if we'll win.
				return move;
			}
		}
		double max = Double.NEGATIVE_INFINITY;
		move = null;
		for(Map.Entry<String, Double> entry: moves.entrySet()) {
			if(entry.getValue() > max) {
				max = entry.getValue();
				move = entry.getKey();
			}
		}
		return move;
	}
	
	/**
	 * Return all the resources proposing the move move.
	 * @param move The move that is proposed.
	 * @return The resources.
	 */
	private Map<Integer, Double> getMoveResourcesConfidence(String move){
		Set<Resource> moveResources = new HashSet<Resource>();
		Map<Integer, Double> resourcesConfidence = new HashMap<Integer, Double>();
		double scoreMax = Double.NEGATIVE_INFINITY;
		for(Resource r : resources) {
			for(MoveSuggestion moveSug : r.getMoveSuggestions()) {
				if(moveSug.getMove().equals(move)) {
					if(moveSug.getScore() > scoreMax) {
						scoreMax = moveSug.getScore();
					}
					moveResources.add(r);
				}
			}
		}
		
		for(Resource r: moveResources) {
			for(MoveSuggestion moveSug: r.getMoveSuggestions()) {
				if(moveSug.getMove().equals(move)) {
					if(scoreMax>0) {
						resourcesConfidence.put(r.getId(), moveSug.getScore()/scoreMax);
					} else if(scoreMax<0) {
						resourcesConfidence.put(r.getId(), scoreMax/moveSug.getScore());
					} else {
						resourcesConfidence.put(r.getId(), (double)0);
					}
				}
			}
		}
		
		return resourcesConfidence;
	}
	
	/**
	 * Return all the Suggestions made by the resources
	 * @return the Suggestions
	 */
	private Set<MoveSuggestion> getSuggestions() {
		HashSet<MoveSuggestion> moves = new HashSet<MoveSuggestion>();
		for(Resource r: this.resources) {
			moves.addAll(r.getMoveSuggestions());
		}
		return moves;
	}
	
	/**
	 * Reward the resources depending on their participation in the game.
	 * @param gameId The game id.
	 * @param gameResult The game result: -1 for lose, 1 for win, 0 for draw.
	 */
	public void rewardResources(int gameId, int gameResult) {
		if(gameResult!=EndingSuggestion.DRAW_RESULT) {
			Map<Integer, Double> resourcesInvolvement = GamesManager.getResourcesInvolvement(gameId);
			ResourcesManager.updateResourcesTrust(resourcesInvolvement, gameResult);
		}
	}
	
	/**
	 * Get a resource by its id.
	 * @param id The id of the resource.
	 * @return The resource.
	 */
	private Resource getResource(int id) {
		for(Resource r: this.resources) {
			if(r.getId()==id) {
				return r;
			}
		}
		return null;
	}

	/**
	 * Ask for all resources to update their suggestions of move.
	 * Do it using multithreading.
	 * Wait for the end of all updates.
	 * @param fen The FEN.
	 */
	private void updateResources(final String fen) {
		Set<Thread> threads = new HashSet<Thread>();
		// Start the requests on different threads:
		for(final Resource resource: this.resources) {
			Thread thread = new Thread(new Runnable() {
				@Override
				public void run() {
					resource.query(fen);
				}
			});
			thread.start();
			threads.add(thread);
		}
		// Wait for all the threads to end:
		for(Thread thread: threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				// Shouldn't happen: we never interrupt a thread.
				System.err.println("The thread was interrupted: "+e.getMessage());
			}
		}
	}
}