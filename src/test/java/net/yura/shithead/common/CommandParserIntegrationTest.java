package net.yura.shithead.common;

import net.yura.cardsengine.Deck;
import net.yura.shithead.common.json.SerializerUtil;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class CommandParserIntegrationTest {

    @Test
    public void testHardcoded2PlayerGame() throws Exception {
        CommandParser parser = new CommandParser();
        ShitheadGame game = new ShitheadGame(2);
        Deck deck = game.getDeck();
        deck.setRandom(new Random(123)); // Fixed seed for predictable game
        game.deal();

        List<String> commands = Files.readAllLines(Path.of(getClass().getResource("/hardcoded-game-sequence.txt").toURI()));

        for (String command : commands) {
            if (game.isFinished()) {
                fail("command not needed: " + command);
            }
            parser.parse(game, command);
        }

        assertTrue(game.isFinished(), "Game should be finished After the hardcoded sequence of moves.");
        assertEquals(1, game.getPlayers().size(), "There should be only one player left.");

        Player loser = game.getPlayers().get(0);
        assertEquals("[5D, 7H, 3D, JC, JH, KD, KS]", loser.getHand().toString());
        assertEquals("[]", loser.getUpcards().toString());
        assertEquals("[AD, 2D]", loser.getDowncards().toString());
    }

    @Test
    public void testHardcoded2DeckGame() throws Exception {
        CommandParser parser = new CommandParser();
        ShitheadGame game = new ShitheadGame(6, new Deck(2));
        Deck deck = game.getDeck();
        deck.setRandom(new Random(123)); // Fixed seed for predictable game
        game.deal();

        List<String> commands = Files.readAllLines(Path.of(getClass().getResource("/2decks-game-sequence.txt").toURI()));

        for (String command : commands) {
            if (game.isFinished()) {
                fail("command not needed: " + command);
            }
            parser.parse(game, command);
        }

        assertTrue(game.isFinished(), "Game should be finished After the hardcoded sequence of moves.");
        assertEquals(1, game.getPlayers().size(), "There should be only one player left.");

        Player loser = game.getPlayers().get(0);
        assertEquals("[]", loser.getHand().toString());
        assertEquals("[8D]", loser.getUpcards().toString());
        assertEquals("[3D, AH, 8S]", loser.getDowncards().toString());
    }

    @Test
    public void testSpectatorGame() throws Exception {
        CommandParser parser = new CommandParser();
        ShitheadGame game = new ShitheadGame(2);
        Deck deck = game.getDeck();
        deck.setRandom(new Random(123)); // Fixed seed
        game.deal();

        // Ready both players using AutoPlay
        while (game.isRearranging()) {
            Player notReady = game.getPlayers().stream()
                .filter(p -> !game.getPlayersReady().contains(p))
                .findFirst().get();
            String command = AutoPlay.getValidGameCommand(game, notReady.getName());
            parser.execute(game, CommandParser.getMutationCommand(game, command));
        }

        // Play a few turns to get the game into an interesting state
        for (int i = 0; i < 6; i++) {
            String command = AutoPlay.getValidGameCommand(game);
            parser.parse(game, command);
        }

        // Serialize from a spectator's POV
        String spectatorJson = SerializerUtil.toJSON(game, "spectator");

        // Deserialize back
        ShitheadGame spectatorGame = SerializerUtil.fromJSON(spectatorJson);

        // Continue a few more turns, keeping both views in sync
        for (int i = 0; i < 6; i++) {
            if (game.isFinished()) break;
            String command = AutoPlay.getValidGameCommand(game);
            String mutation = CommandParser.getMutationCommand(game, command);
            parser.execute(game, mutation);
            parser.execute(spectatorGame, mutation);
        }

        String json1 = SerializerUtil.toJSON(game, "spectator");
        String json2 = SerializerUtil.toJSON(spectatorGame, "spectator");

        assertEquals(json1, json2, "Spectator game view should match full game view");
    }
}