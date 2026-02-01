package net.yura.shithead.server;

import net.yura.lobby.client.Connection;
import net.yura.lobby.client.LobbyClient;
import net.yura.lobby.client.LobbyCom;
import net.yura.lobby.model.Game;
import net.yura.lobby.model.GameType;
import net.yura.lobby.model.Player;
import net.yura.lobby.netty.Server;
import net.yura.shithead.common.AutoPlay;
import net.yura.shithead.common.CommandParser;
import net.yura.shithead.common.ShitheadGame;
import net.yura.shithead.common.json.SerializerUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

public class ServerTest {

    private static final String PLAYER_1_NAME = "test-normal";
    private static final String PLAYER_2_NAME = "test-guest";

    private static final VerificationWithTimeout TIMEOUT = timeout(1000);

    private static Server server;

    static class MockClient implements AutoCloseable {
        private String username;
        final Connection connection;
        final LobbyClient clientMock;
        ShitheadGame game;

        MockClient(String username, int type) {
            this.username = username;
            connection = new LobbyCom(username + "-uuid", "junit-test", "1.0");
            clientMock = Mockito.mock(LobbyClient.class);
            connection.addEventListener(clientMock);
            connection.connect("localhost", server.getPort());
            verify(clientMock, TIMEOUT).connected();
            verify(clientMock, TIMEOUT).setUsername(username, type);
        }

        public void close() {
            connection.disconnect();
        }

        public void setGame(Object gameObj) {
            System.out.println("Game for: " + username + " " + gameObj);
            assertNotNull(gameObj);
            game = SerializerUtil.fromJSON((String) gameObj);
        }

        public void mutateGame(Object mutation) {
            new CommandParser().execute(game, (String) mutation);
        }

        private void resignGame(int id) {
            connection.leaveGame(id);
        }
    }

    private static MockClient mockClient1;
    private static MockClient mockClient2;

    // TODO when updated to next server version, can change this back to BeforeAll/AfterAll
    // currently the rename player test messes up other tests as its not possible to reset
    //@BeforeAll
    @BeforeEach
    public void setupServer() throws Exception {

        server = TestServer.startTestServer(0);

        mockClient1 = new MockClient(PLAYER_1_NAME, Player.PLAYER_NORMAL);
        mockClient2 = new MockClient(PLAYER_2_NAME, Player.PLAYER_GUEST);
    }

    //@AfterAll
    @AfterEach
    public void stopServer() {
        mockClient1.close();
        mockClient1 = null;
        mockClient2.close();
        mockClient2 = null;

        server.shutdown();
        server = null;
    }

    public int bothPlayersJoinGame() {

        // player 1 register for updates
        mockClient1.connection.getGameTypes();
        GameType shithead = getGameTypeFromServer(mockClient1.clientMock, TestServer.GAME_TYPE_NAME);
        mockClient1.connection.getGames(shithead);

        // player 1 create new game
        Game newGame = new Game("test game", null, 2, 100);
        newGame.setType(shithead);
        mockClient1.connection.createNewGame(newGame);

        Game game = getGameFromServer(mockClient1.clientMock);
        System.out.println("Game: " + game);
        assertEquals(1, game.getNumOfPlayers());
        assertEquals(2, game.getMaxPlayers());

        // player 2 register for updates
        mockClient2.connection.getGameTypes();
        mockClient2.connection.getGames(getGameTypeFromServer(mockClient2.clientMock, TestServer.GAME_TYPE_NAME));
        getGameFromServer(mockClient2.clientMock);

        // player 2 joins the game
        mockClient2.connection.joinGame(game.getId(), null);
        game = getGameFromServer(mockClient2.clientMock); // this will actually get called twice, once for joining, once for game asking for input
        System.out.println("Game started: " + game +" " + game.getNumOfPlayers() + "/" + game.getMaxPlayers());
        assertEquals(2, game.getNumOfPlayers());
        assertEquals(2, game.getMaxPlayers());

        mockClient1.connection.playGame(game.getId());
        Object gameObj1 = messageForGame(mockClient1.clientMock, game.getId());
        mockClient1.setGame(gameObj1);

        mockClient2.connection.playGame(game.getId());
        Object gameObj2 = messageForGame(mockClient2.clientMock, game.getId());
        mockClient2.setGame(gameObj2);

        return game.getId();
    }

    @Test
    public void test2PlayersJoinGame() {
        int id = bothPlayersJoinGame();

        readyPlayersForGame(id);

        int maxTurns = 200;
        int turns = 0;
        while (!mockClient1.game.isFinished() || !mockClient2.game.isFinished()) {
            assertEquals(mockClient1.game.isFinished(), mockClient2.game.isFinished());

            String whosTurn = mockClient1.game.getCurrentPlayer().getName();
            if (mockClient1.username.equals(whosTurn)) {
                sendGameMessage(mockClient1, id, AutoPlay.getValidGameCommand(mockClient1.game));
            }
            else if (mockClient2.username.equals(whosTurn)) {
                sendGameMessage(mockClient2, id, AutoPlay.getValidGameCommand(mockClient2.game));
            }
            else {
                throw new IllegalStateException("whos turn??? " + whosTurn);
            }
            assertTrue(turns++ < maxTurns);
        }
    }

    private void readyPlayersForGame(int id) {
        int maxTurns = 50;
        int turns = 0;
        while (mockClient1.game.isRearranging() || mockClient2.game.isRearranging()) {
            assertEquals(mockClient1.game.isRearranging(), mockClient2.game.isRearranging());

            List<net.yura.shithead.common.Player> notReadyPlayers = new ArrayList<>(mockClient1.game.getPlayers());
            notReadyPlayers.removeAll(mockClient1.game.getPlayersReady());
            net.yura.shithead.common.Player notReadyPlayer = notReadyPlayers.get(0);

            if (PLAYER_1_NAME.equals(notReadyPlayer.getName())) {
                sendGameMessage(mockClient1, id, AutoPlay.getValidGameCommand(mockClient1.game, PLAYER_1_NAME));
            }
            else if (PLAYER_2_NAME.equals(notReadyPlayer.getName())) {
                sendGameMessage(mockClient2, id, AutoPlay.getValidGameCommand(mockClient2.game, PLAYER_2_NAME));
            }
            else {
                throw new IllegalStateException();
            }
            assertTrue(turns++ < maxTurns);
        }
    }

    @Test
    public void testMidGameLogin() {
        int gameId = bothPlayersJoinGame();

        // player 1 renames
        String newName = "new name";
        mockClient1.connection.setNick(newName);

        // get the rename command from both players
        Object rename1 = messageForGame(mockClient1.clientMock, gameId);
        Object rename2 = messageForGame(mockClient2.clientMock, gameId);

        assertEquals("rename " + PLAYER_1_NAME + " new+name", rename1);
        assertEquals("rename " + PLAYER_1_NAME + " new+name", rename2);

        // now reset it
        // TODO broken on current version of lobby server, fixed in next version
        //server.getLobbyController().lobby.setNick(newName, "test-normal");
    }


    @Test
    public void testPlayerResignsDuringSetupPhase() {
        int id = bothPlayersJoinGame();

        assertTrue(mockClient1.game.isRearranging());

        mockClient1.resignGame(id);

        applyGameMessages(id, 2);

        String resignedName = PLAYER_1_NAME + "-Resigned";
        assertNotNull(mockClient1.game.getPlayer(resignedName));
        assertTrue(mockClient1.game.isRearranging());
        boolean resignedPlayerReady = mockClient1.game.getPlayersReady().stream()
                .anyMatch(player -> resignedName.equals(player.getName()));
        assertTrue(resignedPlayerReady);
    }

    @Test
    public void testPlayerResignsDuringTheirTurn() {
        int id = bothPlayersJoinGame();
        readyPlayersForGame(id);

        String currentPlayer = mockClient1.game.getCurrentPlayer().getName();
        MockClient resigningClient = PLAYER_1_NAME.equals(currentPlayer) ? mockClient1 : mockClient2;

        resigningClient.resignGame(id);
        applyNextGameMessage(id);

        String resignedName = currentPlayer + "-Resigned";
        assertNotNull(mockClient1.game.getPlayer(resignedName));
        assertEquals(resignedName, mockClient1.game.getCurrentPlayer().getName());
        assertTrue(mockClient1.game.isPlaying());
    }

    @Test
    public void testPlayerResignsDuringOtherPlayersTurn() {
        int id = bothPlayersJoinGame();
        readyPlayersForGame(id);

        String currentPlayer = mockClient1.game.getCurrentPlayer().getName();
        MockClient resigningClient = PLAYER_1_NAME.equals(currentPlayer) ? mockClient2 : mockClient1;

        resigningClient.resignGame(id);
        applyNextGameMessage(id);

        String resignedName = resigningClient.username + "-Resigned";
        assertNotNull(mockClient1.game.getPlayer(resignedName));
        assertEquals(currentPlayer, mockClient1.game.getCurrentPlayer().getName());
        assertTrue(mockClient1.game.isPlaying());
    }


    private static GameType getGameTypeFromServer(LobbyClient mockClient, String name) {
        ArgumentCaptor<List<GameType>> gameTypeCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockClient, TIMEOUT).addGameType(gameTypeCaptor.capture());
        clearInvocations(mockClient);
        List<GameType> gameTypes = gameTypeCaptor.getValue();
        System.out.println("Game Types: " + gameTypes);
        return gameTypes.stream().filter(gt -> name.equals(gt.getName())).findFirst().orElseThrow(() -> new RuntimeException("unable to find: " + name + " in " + gameTypes));
    }

    private static Game getGameFromServer(LobbyClient mockClient) {
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(mockClient, TIMEOUT.atLeastOnce()).addOrUpdateGame(gameCaptor.capture());
        clearInvocations(mockClient);
        return gameCaptor.getValue();
    }

    private void sendGameMessage(MockClient mockClient, int id, String command) {
        mockClient.connection.sendGameMessage(id, command);
        applyNextGameMessage(id);
    }

    private void applyNextGameMessage(int id) {
        applyGameMessages(id, 1);
    }

    private void applyGameMessages(int id, int expectedCount) {
        List<Object> mutations1 = messageForGame(mockClient1.clientMock, id, expectedCount);
        List<Object> mutations2 = messageForGame(mockClient2.clientMock, id, expectedCount);

        for (int i = 0; i < expectedCount; i++) {
            mockClient1.mutateGame(mutations1.get(i));
            mockClient2.mutateGame(mutations2.get(i));
        }
    }

    private static Object messageForGame(LobbyClient mockClient, int id) {
        return messageForGame(mockClient, id, 1).get(0);
    }

    private static List<Object> messageForGame(LobbyClient mockClient, int id, int expectedCount) {
        ArgumentCaptor<Object> gameCaptor = ArgumentCaptor.forClass(Object.class);
        verify(mockClient, TIMEOUT.times(expectedCount)).messageForGame(eq(id), gameCaptor.capture());
        clearInvocations(mockClient);
        return gameCaptor.getAllValues();
    }
}
