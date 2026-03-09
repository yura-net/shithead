package net.yura.shithead.server;

import net.yura.lobby.server.AbstractTurnBasedServerGame;
import net.yura.lobby.server.LobbySession;
import net.yura.shithead.common.AutoPlay;
import net.yura.shithead.common.CommandParser;
import net.yura.shithead.common.Player;
import net.yura.shithead.common.ShitheadGame;
import net.yura.shithead.common.json.SerializerUtil;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ShitHeadServer extends AbstractTurnBasedServerGame {

    private static Logger logger = Logger.getLogger(ShitHeadServer.class.getName());

    ShitheadGame game;
    private final CommandParser commandParser;

    public ShitHeadServer() {
        this.commandParser = new CommandParser();
    }

    @Override
    public void startGame(String[] players) {
        // TODO may need to shuffle the players
        game = new ShitheadGame(Arrays.asList(players));
        game.deal();
        // we are in re-arrange mode, anyone can go

        // TODO as we never call getInputFromClient, we never end up timing out any player if they do not go
        // so game can get stuck if any player does not send ready

        // tell all users the game has started and we want input from all of them
        for (Player player : game.getPlayers()) {
            listoner.needInputFrom(player.getName());
        }
    }

    @Override
    public void clientHasJoined(LobbySession lobbySession) {
        String username = lobbySession.getUsername();
        String json = SerializerUtil.toJSON(game, username);
        // TODO maybe we want to gzip
        listoner.messageFromGame(json, Collections.singletonList(lobbySession));
    }

    @Override
    public void destroyGame() { }

    @Override
    public void playerJoins(String s) { }

    // old method
    public boolean playerResigns(String username) { return playerResigns(username, false); }

    // new method
    public boolean playerResigns(String username, boolean gameTriggered) {
        Player resignedPlayer = game.getPlayer(username);
        if (resignedPlayer == null) {
            throw new IllegalArgumentException("player not in the game " + username +" (gameTriggered=" + gameTriggered + ")");
        }

        // rename the player to a new resigned name both here and on clients
        String newResignedName = username + (gameTriggered ? "-AutoResigned" : "-Resigned");
        String renameCommand = "rename " + CommandParser.encodePlayerName(username) + " " + CommandParser.encodePlayerName(newResignedName);
        commandParser.execute(game, renameCommand);
        // TODO do we need to actually send this out? as clients are already notified when a player resigns from a game
        listoner.messageFromGame(renameCommand, getAllClients());


        // in the case this player is not ready yet, mark them as ready
        if (!game.getPlayersReady().contains(resignedPlayer)) {
            String makePlayerReady = "ready " + CommandParser.encodePlayerName(newResignedName);
            commandParser.execute(game, makePlayerReady);
            listoner.messageFromGame(makePlayerReady, getAllClients());

            if (game.isPlaying()) {
                // if marking this player as ready has transitioned us into the playing state of the game, then we now need input
                getNextTurn();
            }
        }
        //check if we need to take a turn
        else if (resignedPlayer.equals(game.getCurrentPlayer())) {
            getNextTurn();
        }

        List<Player> notResignedPlayers = game.getPlayers().stream().filter(p -> !p.getName().endsWith("Resigned")).collect(Collectors.toList());
        // only schedule game for deletion if game is over or there are no more humans left
        if (game.isFinished() || notResignedPlayers.isEmpty()) {
            // TODO there is a new gameFinished that takes a Map of username -> score
            // we may want to keep a full list of game players from the start somewhere to use that
            // we can get a list of all players from the game by getting game.getReadyPlayers()
            return gameFinished(null); // game.getPlayers().get(0).getName()
        }
        return false;
    }

    @Override
    public void midgamePlayerLogin(String oldName, String newName) {

        // TODO is this really needed, can we just use player events in new lobby version
        //game.renamePlayer(oldName, newName);

        String renameCommand = "rename " + CommandParser.encodePlayerName(oldName) + " " + CommandParser.encodePlayerName(newName);

        commandParser.execute(game, renameCommand);
        listoner.messageFromGame(renameCommand, getAllClients());
    }

    @Override
    public void playerTimedOut(String username) {
        // TODO for now just resign player, if we want to add skips counter then we need to extend TurnBasedGame
        //listoner.sendChatroomMessage(username + " has timed out and has been resigned from the game.");
        listoner.resignPlayer(username); // this will send chat message about AutoResign
    }

    @Override
    public void objectFromPlayer(String username, Object o) {
        String command = (String) o;

        if (game.isRearranging() && !username.equals(CommandParser.decodePlayerName(command.split(" ")[1]))) {
            throw new RuntimeException("not your player");
        }
        if (game.isPlaying() && !username.equals(game.getCurrentPlayer().getName())) {
            logger.warning("not your turn error! currentPlayer=" + game.getCurrentPlayer().getName() +" fromPlayer=" + username +" command=" + command);
            throw new RuntimeException("not your turn");
        }

        String mutation = CommandParser.getMutationCommand(game, command);
        commandParser.execute(game, mutation);

        // after move, notify all players
        Collection<LobbySession> allClients = getAllClients();
        if (mutation.startsWith("play hand ") && mutation.contains("pickup")) {
            // for picking up a card we ONLY want to tell the actual player what the card is
            listoner.messageFromGame(mutation, allClients.stream().filter(c -> username.equals(c.getUsername())).collect(Collectors.toList()));
            listoner.messageFromGame(command, allClients.stream().filter(c -> !username.equals(c.getUsername())).collect(Collectors.toList()));
        }
        else {
            listoner.messageFromGame(mutation, allClients);
        }

        if (game.isPlaying()) {
            getNextTurn();
        }
        // TODO if we have only 1 player left to be marked as ready, we can already set them as current player
        // TODO BUT maybe its good that we dont kick off any timers before everyone is ready,
        // gives everyone a chance to join the game even if the timeout for turns is small
        else if (game.isFinished()) {
            gameFinished(null);
        }
    }

    /**
     * get next command from AI or client app
     */
    private void getNextTurn() {
        Player whosTurn = game.getCurrentPlayer();
        boolean doAITurn = whosTurn.getName().endsWith("Resigned");
        getInputFromClient(doAITurn ? null : whosTurn.getName());

        if (doAITurn) {
            scheduler.schedule(() -> {
                String command = AutoPlay.getValidGameCommand(game);
                String mutation = CommandParser.getMutationCommand(game, command);
                commandParser.execute(game, mutation);
                listoner.messageFromGame(mutation, getAllClients());

                if (game.isFinished()) {
                    gameFinished(null);
                }
                else {
                    getNextTurn();
                }
            }, 2, TimeUnit.SECONDS);
        }
    }

    @Override
    public boolean isSupportedClient(LobbySession lobbySession) {
        return true;
    }

    // old method
    public void loadGame(byte[] bytes) { loadGame(null, bytes); }

    // new method
    public void loadGame(String[] lobbyPlayer, byte[] bytes) {
        game = SerializerUtil.fromJSON(new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public byte[] saveGameState() {
        String json = SerializerUtil.toJSON(game, null);
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
