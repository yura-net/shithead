package net.yura.shithead.common.json;

import net.yura.cardsengine.Card;
import net.yura.cardsengine.Deck;
import net.yura.shithead.common.Player;
import net.yura.shithead.common.ShitheadGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Stack;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SerializerUtilTest {

    private ShitheadGame game;

    @BeforeEach
    public void setUp() {
        Deck deck = new Deck(1);
        deck.setRandom(new Random(123)); // Use a fixed seed for predictable tests
        game = new ShitheadGame(Arrays.asList("Alice", "Bob"), deck);
        game.deal();
        game.playerReady(game.getPlayers().get(0));
    }

    @Test
    public void testFullGameSerializationMatchesSnapshot() throws Exception {
        String expectedJson = loadResourceAsString("/testgame.json");
        String generatedJson = SerializerUtil.toJSON(game, null);
        assertEquals(expectedJson, generatedJson.replaceAll("\\r\\n", "\n"));
    }

    @Test
    public void testPlayerSpecificSerializationMatchesSnapshot() throws Exception {
        String expectedJson = loadResourceAsString("/testgame_alice.json");
        String generatedJson = SerializerUtil.toJSON(game, "Alice");
        assertEquals(expectedJson, generatedJson.replaceAll("\\r\\n", "\n"));
    }

    @Test
    public void testSpectatorSpecificSerializationMatchesSnapshot() throws Exception {
        String expectedJson = loadResourceAsString("/testgame_jill.json");
        String generatedJson = SerializerUtil.toJSON(game, "Jill");
        assertEquals(expectedJson, generatedJson.replaceAll("\\r\\n", "\n"));
    }

    @Test
    public void testFromJson() throws IOException, NoSuchFieldException, IllegalAccessException {
        // Load the game state snapshot from a shared test resource file.
        String jsonSnapshot = loadResourceAsString("/testgame.json");

        // Deserialize the JSON snapshot back to a game object
        ShitheadGame deserializedGame = SerializerUtil.fromJSON(jsonSnapshot);

        // Assert that the deserialized game state is identical to the snapshot
        assertNull(deserializedGame.getCurrentPlayer());
        assertTrue(deserializedGame.getWastePile().isEmpty());
        assertEquals(2, deserializedGame.getPlayers().size());

        // Assert player 1 state
        Player p1 = deserializedGame.getPlayers().get(0);
        assertEquals("Alice", p1.getName());
        assertCardListsEqual(Arrays.asList("7D", "QS", "9S"), p1.getHand());
        assertCardListsEqual(Arrays.asList("6H", "3C", "JH"), p1.getUpcards());
        assertCardListsEqual(Arrays.asList("5D", "AD", "2D"), p1.getDowncards());

        // Assert player 2 state
        Player p2 = deserializedGame.getPlayers().get(1);
        assertEquals("Bob", p2.getName());
        assertCardListsEqual(Arrays.asList("2C", "JC", "8C"), p2.getHand());
        assertCardListsEqual(Arrays.asList("XH", "3S", "KD"), p2.getUpcards());
        assertCardListsEqual(Arrays.asList("9C", "9H", "AS"), p2.getDowncards());

        // Assert deck state
        List<String> expectedDeck = Arrays.asList("XD", "QD", "6D", "6S", "4H", "5C", "7H", "4C", "6C", "4S", "8D", "KS", "3D", "AH", "XC", "XS", "QH", "9D", "QC", "JS", "KH", "3H", "8S", "2S", "JD", "8H", "5H", "2H", "5S", "7S", "AC", "4D", "KC", "7C");
        assertCardListsEqual(expectedDeck, getDeckCards(deserializedGame.getDeck()));
    }

    @Test
    public void testSevenGoLowSerializedWhenTrue() throws Exception {
        game.setSevenGoLow(true);
        String json = SerializerUtil.toJSON(game, null);
        assertTrue(json.contains("\"sevenGoLow\" : true"), "Expected sevenGoLow to be true in serialized JSON");
    }

    @Test
    public void testSevenGoLowDeserializationRoundtrip() throws Exception {
        game.setSevenGoLow(true);
        String json = SerializerUtil.toJSON(game, null);
        ShitheadGame deserialized = SerializerUtil.fromJSON(json);
        assertTrue(deserialized.isSevenGoLow());
    }

    @Test
    public void testSevenGoLowDefaultIsFalseAfterDeserialization() throws IOException {
        String jsonSnapshot = loadResourceAsString("/testgame.json");
        ShitheadGame deserialized = SerializerUtil.fromJSON(jsonSnapshot);
        assertFalse(deserialized.isSevenGoLow());
    }

    @Test
    void testDeserializationPlayerSpecific() throws Exception {
        // Load the player-specific JSON file
        String json = loadResourceAsString("/testgame_alice.json");

        // Deserialize the game state
        ShitheadGame game = net.yura.shithead.common.json.SerializerUtil.fromJSON(json);

        // Assert general game state
        assertNull(game.getCurrentPlayer());
        assertTrue(game.getWastePile().isEmpty());

        // Assert deck state using reflection
        Deck deck = game.getDeck();
        java.lang.reflect.Field cardsField = Deck.class.getDeclaredField("cards");
        cardsField.setAccessible(true);
        java.util.Stack<Card> cards = (java.util.Stack<Card>) cardsField.get(deck);
        assertEquals(34, cards.size());

        // Assert Alice's state (cards are visible)
        Player alice = game.getPlayers().get(0);
        assertEquals("Alice", alice.getName());
        assertEquals(3, alice.getUpcards().size());
        assertFalse(alice.getUpcards().contains(null)); // Should contain actual cards
        assertEquals(3, alice.getHand().size());
        assertFalse(alice.getHand().contains(null));
        assertEquals(3, alice.getDowncards().size()); // Downcards are never visible
        assertTrue(alice.getDowncards().stream().allMatch(c -> c == null));


        // Assert Bob's state (cards are hidden)
        Player bob = game.getPlayers().get(1);
        assertEquals("Bob", bob.getName());
        assertEquals(3, bob.getUpcards().size());
        assertFalse(bob.getUpcards().contains(null)); // Upcards are always visible
        assertEquals(3, bob.getHand().size());
        assertTrue(bob.getHand().stream().allMatch(c -> c == null)); // Hand is hidden
        assertEquals(3, bob.getDowncards().size());
        assertTrue(bob.getDowncards().stream().allMatch(c -> c == null));
    }

    private List<Card> getDeckCards(Deck deck) throws NoSuchFieldException, IllegalAccessException {
        // The Deck class from the external cardsengine.jar library does not provide a
        // public method to get the list of cards. Reflection is used here as a
        // workaround to access the internal state of the deck for assertion purposes.
        Field cardsField = Deck.class.getDeclaredField("cards");
        cardsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Stack<Card> cards = (Stack<Card>) cardsField.get(deck);
        return cards;
    }

    private void assertCardListsEqual(List<String> expected, List<Card> actual) {
        List<String> actualStrings = actual.stream().map(Card::toString).collect(Collectors.toList());
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actualStrings.get(i));
        }
    }

    public static String loadResourceAsString(String path) throws IOException {
        try (InputStream is = SerializerUtilTest.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Resource not found: " + path);
            }
            try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                 BufferedReader br = new BufferedReader(isr)) {
                return br.lines().collect(Collectors.joining("\n"));
            }
        }
    }
}
