package net.yura.shithead.common;

import net.yura.cardsengine.Card;
import net.yura.shithead.common.json.SerializerUtil;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CommandParser {

    /**
     * commands from UI, can be used for playing hidden cards
     */
    public void parse(ShitheadGame game, String command) {
        execute(game, getMutationCommand(game, command));
    }

    /**
     * This must be called on a FULL game, NOT spectator game
     * @see SerializerUtil#toJSON(ShitheadGame, String)
     */
    public static String getMutationCommand(ShitheadGame game, String command) {
        if (command.startsWith("play down ")) {
            int index = Integer.parseInt(command.substring("play down ".length()));
            Player player = game.getCurrentPlayer();
            Card card = player.getDowncards().get(index);
            return "play down " + card;
        }
        if (command.startsWith("play hand ")) {
            int numCards = command.split(" ").length - 2;
            List<Card> cards = game.getDeck().getCards().subList(Math.max(0, game.getDeck().getCards().size() - numCards), game.getDeck().getCards().size());
            if (!cards.isEmpty()) {
                return command + " pickup " + cards.stream().map(Object::toString).collect(Collectors.joining(" "));
            }
        }
        if (command.equals("play deck")) {
            Card card = game.getTopCardFromDeck();
            if (card == null) {
                throw new IllegalStateException("no cards in deck");
            }
            return "play deck " + card;
        }

        return command;
    }

    /**
     * game engine mutation command (no hidden cards) can be called on spectator game
     */
    public void execute(ShitheadGame game, String command) {

        String[] tokens = command.split(" ");
        if (tokens.length == 0) {
            throw new IllegalArgumentException("empty command");
        }

        switch (tokens[0]) {
            case "swap":
                if (!game.isRearranging()) {
                    throw new IllegalArgumentException("game not in rearranging mode");
                }
                if (tokens.length != 4) {
                    throw new IllegalArgumentException("incomplete rearrange command");
                }
                String playerName = decodePlayerName(tokens[1]);
                Player player = game.getPlayer(playerName);
                if (player == null) {
                    throw new IllegalArgumentException("player not found: " + playerName);
                }
                Card handCard = SerializerUtil.cardFromString(tokens[2]);
                Card upCard = SerializerUtil.cardFromString(tokens[3]);
                game.rearrangeCards(player, handCard, upCard);
                return;
            case "ready":
                if (!game.isRearranging()) {
                    throw new IllegalArgumentException("game not in rearranging mode");
                }
                if (tokens.length != 2) {
                    throw new IllegalArgumentException("incomplete ready command");
                }
                playerName = decodePlayerName(tokens[1]);
                player = game.getPlayer(playerName);
                if (player == null) {
                    throw new IllegalArgumentException("player not found: " + playerName);
                }
                game.playerReady(player);
                return;
            case "rule":
                if (tokens.length != 3) {
                    throw new IllegalArgumentException("incomplete rule command");
                }
                if (!tokens[1].equalsIgnoreCase("golow")) {
                    throw new IllegalArgumentException("unknown rule: " + tokens[1]);
                }
                switch (tokens[2].toLowerCase()) {
                    case "on":
                        game.setGoLowRuleEnabled(true);
                        break;
                    case "off":
                        game.setGoLowRuleEnabled(false);
                        break;
                    default:
                        throw new IllegalArgumentException("rule value must be 'on' or 'off'");
                }
                return;
            case "play":
                if (!game.isPlaying()) {
                    throw new IllegalArgumentException("game not in playing mode");
                }
                if (tokens.length < 3) {
                    throw new IllegalArgumentException("incomplete play command");
                }
                List<Card> cards;
                boolean isDowncardPlay = false;
                switch (tokens[1]) {
                    case "hand":
                        int end = tokens.length;
                        int pickUpIndex = Arrays.asList(tokens).indexOf("pickup");
                        if (pickUpIndex >= 0) {
                            end = pickUpIndex;

                            for (int i = pickUpIndex + 1; i < tokens.length; i++) {
                                Card card = SerializerUtil.cardFromString(tokens[i]);
                                List<Card> deck = game.getDeck().getCards();
                                int indexInDeck = deck.size() - tokens.length + i;
                                if (deck.get(indexInDeck) == null) {
                                    deck.set(indexInDeck, card);
                                }
                                else if (!card.equals(deck.get(indexInDeck))) {
                                    throw new IllegalStateException("cards not equal");
                                }
                            }
                        }
                        cards = Arrays.asList(tokens).subList(2, end).stream().map(SerializerUtil::cardFromString).collect(Collectors.toList());
                        break;
                    case "up":
                        List<Card> upCardsToPlay = new ArrayList<>();
                        for (int i = 2; i < tokens.length; i++) {
                            upCardsToPlay.add(SerializerUtil.cardFromString(tokens[i]));
                        }
                        cards = upCardsToPlay;
                        break;
                    case "down":
                        isDowncardPlay = true;
                        if (tokens.length != 3) {
                            throw new IllegalArgumentException("can only play one downcard at a time");
                        }
                        Card card = SerializerUtil.cardFromString(tokens[2]);
                        cards = Collections.singletonList(card);
                        break;
                    case "deck":
                        if (tokens.length != 3) {
                            throw new IllegalArgumentException("incomplete play deck command");
                        }
                        game.playCardFromDeck(SerializerUtil.cardFromString(tokens[2]));
                        return;
                    default:
                        throw new IllegalArgumentException("invalid card source: " + tokens[1]);
                }

                if (!game.playCards(cards)) {
                    if (isDowncardPlay) {
                        // A failed downcard play is a valid game event, not an error.
                        // The penalty is handled by the game logic.
                    } else {
                        throw new IllegalArgumentException("invalid move");
                    }
                }
                return;

            case "pickup":
                if (!game.isPlaying()) {
                    throw new IllegalArgumentException("game not in playing mode");
                }
                game.pickUpWastePile();
                return;

            // TODO this may not be needed as a game command as server and client can handle themselves
            case "rename":
                if (tokens.length != 3) {
                    throw new IllegalArgumentException("incomplete rename command");
                }
                String oldName = decodePlayerName(tokens[1]);
                String newName = decodePlayerName(tokens[2]);
                game.renamePlayer(oldName, newName);
                return;

            default:
                throw new IllegalArgumentException("unknown command: " + tokens[0]);
        }
    }

    public static String decodePlayerName(String encoded) {
        try {
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String encodePlayerName(String encoded) {
        try {
            return URLEncoder.encode(encoded, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}