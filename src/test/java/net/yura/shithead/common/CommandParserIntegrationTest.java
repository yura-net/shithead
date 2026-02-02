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
        assertEquals(1, game.getPlayers().size(), "There should be only one player left (the winner).");
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
        assertEquals(1, game.getPlayers().size(), "There should be only one player left (the winner).");
    }

    @Test
    public void testSpectatorGame() throws Exception {
        // Setup a game to a certain point
        CommandParser parser = new CommandParser();
        ShitheadGame game = new ShitheadGame(2);
        Deck deck = game.getDeck();
        deck.setRandom(new Random(123)); // Fixed seed
        game.deal();

        String[] setupCommands = {
            "ready Player+1", "ready Player+2",
            "play hand 7D", "play hand 2C", "play hand QS", "play hand KC", "pickup", "play hand JC"
        };

        for (String command : setupCommands) {
            parser.parse(game, command);
        }

        // Serialize from a spectator's POV
        String spectatorJson = net.yura.shithead.common.json.SerializerUtil.toJSON(game, "spectator");

        // Deserialize back
        ShitheadGame spectatorGame = net.yura.shithead.common.json.SerializerUtil.fromJSON(spectatorJson);

        // Continue the game with the spectator's view
        String[] subsequentCommands = {
            "play hand 2C", "play hand 8C", "play hand 9S", "pickup", "play hand 7C", "play hand 7S"
        };

        for (String command : subsequentCommands) {
            String mutation = CommandParser.getMutationCommand(game, command);
            parser.execute(game, mutation); // at each step we need to also update the full game

            parser.execute(spectatorGame, mutation);
        }

        String json1 = SerializerUtil.toJSON(game, "spectator");
        String json2 = SerializerUtil.toJSON(spectatorGame, "spectator");

        assertEquals(json1, json2);

        assertEquals("Player 1", spectatorGame.getCurrentPlayer().getName());
    }
}
