package net.yura.shithead.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import net.yura.cardsengine.Card;
import net.yura.cardsengine.Deck;
import net.yura.shithead.common.Player;
import net.yura.shithead.common.ShitheadGame;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class GameDeserializer extends StdDeserializer<ShitheadGame> {

    public GameDeserializer() {
        this(null);
    }

    protected GameDeserializer(Class<?> vc) {
        super(vc);
    }

    static List<Card> readCardsOrCount(JsonParser jp) throws IOException {
        if (jp.getCurrentToken() == JsonToken.VALUE_NUMBER_INT) {
            int count = jp.getIntValue();
            return Collections.nCopies(count, null);
        } else {
            return jp.readValueAs(new TypeReference<List<Card>>() {});
        }
    }

    @Override
    public ShitheadGame deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        List<String> playersReady = new ArrayList<>();
        String currentPlayerName = null;
        List<Player> players = null;
        List<Card> deckCards = null;
        List<Card> wastePile = null;
        boolean sevenGoLow = false;

        while (jp.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = jp.getCurrentName();
            jp.nextToken(); // Move to the value

            if ("playersReady".equals(fieldName)) {
                playersReady = jp.readValueAs(new TypeReference<List<String>>() {});
            } else if ("currentPlayerName".equals(fieldName)) {
                currentPlayerName = jp.getText();
            } else if ("players".equals(fieldName)) {
                players = jp.readValueAs(new TypeReference<List<Player>>() {});
            } else if ("deck".equals(fieldName) || "cardsInDeck".equals(fieldName)) {
                deckCards = readCardsOrCount(jp);
            } else if ("wastePile".equals(fieldName)) {
                wastePile = readCardsOrCount(jp);
            } else if ("sevenGoLow".equals(fieldName)) {
                sevenGoLow = jp.getBooleanValue();
            } else {
                jp.skipChildren();
            }
        }

        // The Deck class from the external cardsengine.jar library does not provide a
        // public method to set its internal card collection. Reflection is used here
        // as a workaround to inject the deserialized list of cards into the Deck.
        Stack<Card> deckStack = new Stack<>();
        if (deckCards != null) {
            deckStack.addAll(deckCards);
        }
        Deck deck = new Deck(0); // Create an empty deck
        try {
            Field cardsField = Deck.class.getDeclaredField("cards");
            cardsField.setAccessible(true);
            cardsField.set(deck, deckStack);
        } catch (Exception e) {
            throw new RuntimeException("Could not set deck cards via reflection", e);
        }

        // Create the game object and populate it using the setters
        ShitheadGame game = new ShitheadGame(players.size(), deck);
        Set<Player> playersReadySet = new HashSet<>();
        for (String playerName : playersReady) {
            for (Player p : players) {
                if (p.getName().equals(playerName)) {
                    playersReadySet.add(p);
                    break;
                }
            }
        }
        game.setPlayersReady(playersReadySet);
        game.setPlayers(players);
        game.setWastePile(wastePile);
        game.setSevenGoLow(sevenGoLow);

        // Set the current player index
        if (currentPlayerName != null) {
            for (int i = 0; i < players.size(); i++) {
                if (players.get(i).getName().equals(currentPlayerName)) {
                    game.setCurrentPlayer(i);
                    break;
                }
            }
        }

        return game;
    }
}