package net.yura.shithead.common;

import net.yura.cardsengine.Card;
import net.yura.cardsengine.CardDeckEmptyException;
import net.yura.cardsengine.Deck;
import net.yura.cardsengine.Rank;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Basic implementation of the Shithead card game rules.
 * <p>
 * This class manages the deck, waste pile and players while exposing
 * helper methods to play cards and progress the game. It aims to mirror
 * the description of the game found on its Wikipedia page.
 */
public class ShitheadGame {

    private boolean goLowRuleEnabled = true;
    public boolean isGoLowRuleEnabled() { return goLowRuleEnabled; }
    public void setGoLowRuleEnabled(boolean goLowRuleEnabled) { this.goLowRuleEnabled = goLowRuleEnabled; }

    private Set<Player> playersReady = new HashSet<>();
    private List<Player> players = new ArrayList<>();
    private final Deck deck;
    private List<Card> wastePile = new ArrayList<>();

    private int currentPlayer = -1;

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public void setWastePile(List<Card> wastePile) {
        this.wastePile = wastePile;
    }

    public void setCurrentPlayer(int currentPlayer) {
        this.currentPlayer = currentPlayer;
    }

    /**
     * Creates a new game with the given number of players.
     *
     * @param playerCount number of players taking part
     */
    public ShitheadGame(int playerCount) {
        this(playerCount, new Deck(1));
    }

    /**
     * Creates a new game with the given number of players and a specific deck.
     * This is useful for testing with a predictable deck.
     *
     * @param playerCount number of players taking part
     * @param deck The deck to be used in the game.
     */
    public ShitheadGame(int playerCount, Deck deck) {
        for (int i = 0; i < playerCount; i++) {
            players.add(new Player("Player " + (i + 1)));
        }
        this.deck = deck;
    }

    /**
     * Creates a new game with a list of named players.
     * @param playerNames list of player names
     */
    public ShitheadGame(List<String> playerNames) {
        this(playerNames, new Deck(1));
    }

    /**
     * Creates a new game with a list of named players and a specific deck.
     * @param playerNames list of player names
     * @param deck The deck to be used in the game.
     */
    public ShitheadGame(List<String> playerNames, Deck deck) {
        for (String name : playerNames) {
            players.add(new Player(name));
        }
        this.deck = deck;
    }

    /**
     * Deals three downcards, three upcards and three hand cards to each player.
     */
    public void deal() {
        if (players.stream().mapToInt(Player::getNoCards).sum() > 0) {
            throw new IllegalStateException("Cards have already been dealt.");
        }
        deck.shuffle();
        try {
            for (int i = 0; i < 3; i++) {
                for (Player p : players) {
                    p.getDowncards().add(deck.dealCard());
                }
            }
            for (int i = 0; i < 3; i++) {
                for (Player p : players) {
                    p.getUpcards().add(deck.dealCard());
                }
            }
            for (int i = 0; i < 3; i++) {
                for (Player p : players) {
                    p.getHand().add(deck.dealCard());
                }
            }
        }
        catch (CardDeckEmptyException ex) {
            throw new IllegalStateException("not enough cards in deck for initial deal", ex);
        }
    }

    public void rearrangeCards(Player player, Card card1, Card card2) {
        if (!isRearranging()) {
            throw new IllegalStateException("Game is not in REARRANGING state.");
        }
        if (playersReady.contains(player)) {
            throw new IllegalStateException("Player is already ready and cannot rearrange cards.");
        }

        List<Card> upCards = player.getUpcards();
        List<Card> handCards = player.getHand();

        Card upCard;
        Card handCard;

        if (upCards.contains(card1) && (handCards.contains(card2) || handCards.contains(null))) {
            upCard = card1;
            handCard = card2;
        }
        else if (upCards.contains(card2) && (handCards.contains(card1) || handCards.contains(null))) {
            upCard = card2;
            handCard = card1;
        }
        else {
            if (!handCards.contains(card1) && !handCards.contains(card2)) {
                throw new IllegalArgumentException("Player does not have the specified card in their hand.");
            }
            if (!upCards.contains(card1) && !upCards.contains(card2)) {
                throw new IllegalArgumentException("Player does not have the specified card in their upcards.");
            }
            // This covers swapping two hand cards, or two upcards
            throw new IllegalArgumentException("Cards must be from different piles.");
        }

        int upIndex = upCards.indexOf(upCard);
        int handIndex = handCards.indexOf(handCard);
        if (handIndex < 0) handIndex = handCards.indexOf(null);

        upCards.set(upIndex, handCard);
        handCards.set(handIndex, upCard);
    }

    public void playerReady(Player player) {
        if (!isRearranging()) {
            throw new IllegalStateException("Game is not in REARRANGING state.");
        }
        playersReady.add(player);

        if (isPlaying()) {
            chooseFirstPlayer();
        }
    }

    /**
     * pick the first player based on who has the lowest upcard
     */
    private void chooseFirstPlayer() {
        int bestRank = Integer.MAX_VALUE;
        Player firstPlayer = null;

        for (Player pc : players) {
            Card c = findLowestStartCard(pc.getUpcards());
            if (c != null) {
                int rank = c.getRank().toInt();
                if (rank < bestRank) {
                    bestRank = rank;
                    firstPlayer = pc;
                }
            }
        }

        // in very rare situations no player will have a valid starting card
        // in this case the player at index 0 will go first
        currentPlayer = firstPlayer == null ? 0 : players.indexOf(firstPlayer);
    }

    public static Card findLowestStartCard(Collection<Card> cards) {
        int bestRank = Integer.MAX_VALUE;
        Card lowest = null;
        for (Card c : cards) {
            int rank = c.getRank().toInt();
            if (rank >= 3 && rank < bestRank) {
                bestRank = rank;
                lowest = c;
            }
        }
        return lowest;
    }

    /**
     * Returns the player whose turn it is.
     */
    public Player getCurrentPlayer() {
        return currentPlayer == -1 ? null : players.get(currentPlayer);
    }

    /**
     * Returns the list of players in the game.
     */
    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    /**
     * Plays a set of cards from the player's hand or table. The cards must all
     * be of the same rank and be valid according to the top of the waste pile.
     *
     * @param cards list of cards to play
     * @return {@code true} if the play was successful, {@code false} otherwise
     */
    public boolean playCards(List<Card> cards) {
        if (!isPlaying()) {
            throw new IllegalStateException("Game is not in PLAYING state.");
        }
        Player player = getCurrentPlayer();

        if (cards.isEmpty()) {
            return false;
        }

        // all cards must share rank
        Rank rank = cards.get(0).getRank();
        for (Card c : cards) {
            if (c.getRank() != rank) {
                return false;
            }
        }

        List<Card> sourcePile;
        if (!player.getHand().isEmpty()) {
            sourcePile = player.getHand();
        } else if (!player.getUpcards().isEmpty()) {
            sourcePile = player.getUpcards();
        } else if (!player.getDowncards().isEmpty()) {
            sourcePile = player.getDowncards();
        } else {
            return false; // Player has no cards.
        }

        // --- Validation for card ownership (handles spectator mode) ---
        List<Card> pileCopy = new ArrayList<>(sourcePile);
        for (Card card : cards) {
            if (!pileCopy.remove(card)) { // Try to remove the specific card
                if (!pileCopy.remove(null)) { // If not found, try to remove a null placeholder
                    return false; // Player has neither the card nor a placeholder for it
                }
            }
        }

        // --- Validation for game rules ---
        Card top = wastePile.isEmpty() ? null : wastePile.get(wastePile.size() - 1);
        if (!isPlayable(rank, top)) {
            if (sourcePile == player.getDowncards()) {
                // Penalty for invalid down-card play
                // We must remove the card from downcards and add it to hand before picking up pile
                for (Card card : cards) {
                    if (!sourcePile.remove(card)) {
                        sourcePile.remove(null); // Known to succeed due to validation above
                    }
                }
                player.getHand().addAll(cards);
                pickUpWastePile();
            }
            return false;
        }

        // --- Execution: Remove cards from the actual source pile ---
        for (Card card : cards) {
            if (!sourcePile.remove(card)) {
                sourcePile.remove(null); // Known to succeed due to validation above
            }
        }
        wastePile.addAll(cards);

        // apply special rules
        boolean burned = applySpecialRules(rank);

        refillHand(player);

        boolean playerWon = player.getHand().isEmpty() && player.getUpcards().isEmpty() && player.getDowncards().isEmpty();

        if (playerWon) {
            players.remove(player);
            if (currentPlayer >= players.size()) {
                currentPlayer = 0;
            }
        } else if (!burned) {
            advanceTurn();
        }

        return true;
    }

    /**
     * The current player picks up all the cards from the waste pile.
     * This is usually done when the player cannot play any of their cards.
     * After picking up the pile, the turn advances to the next player.
     */
    public void pickUpWastePile() {
        if (isFinished()) {
            throw new IllegalStateException("game finished");
        }
        Player player = getCurrentPlayer();
        player.getHand().addAll(wastePile);
        wastePile.clear();
        advanceTurn();
    }

    private boolean applySpecialRules(Rank rank) {
        boolean burned = false;
        // Ten burns the pile
        if (rank == Rank.TEN) {
            wastePile.clear();
            burned = true;
        }
        // Four of a kind burns the pile
        if (wastePile.size() >= 4) {
            int size = wastePile.size();
            Rank r1 = wastePile.get(size - 1).getRank();
            Rank r2 = wastePile.get(size - 2).getRank();
            Rank r3 = wastePile.get(size - 3).getRank();
            Rank r4 = wastePile.get(size - 4).getRank();
            if (r1 == r2 && r2 == r3 && r3 == r4) {
                wastePile.clear();
                burned = true;
            }
        }
        return burned;
    }



    public boolean isPlayable(Rank rank, Card top) {
        if (rank == Rank.TWO || rank == Rank.TEN || top == null) {
            return true;
        }
        if (top.getRank() == Rank.TWO) {
            return true;
        }
        if (goLowRuleEnabled && top.getRank() == Rank.SEVEN) {
            return AcesHighCardComparator.getRankValue(rank) < AcesHighCardComparator.getRankValue(top.getRank());
        }
        return AcesHighCardComparator.getRankValue(rank) >= AcesHighCardComparator.getRankValue(top.getRank());
    }


    private void advanceTurn() {
        currentPlayer = (currentPlayer + 1) % players.size();
    }

    private void refillHand(Player player) {
        try {
            while (player.getHand().size() < 3) {
                player.getHand().add(deck.dealCard());
            }
        }
        catch (CardDeckEmptyException ex) {
            // this is fine, the deck is empty now
        }
    }

    /**
     * Returns the current waste pile as an unmodifiable list.
     */
    public List<Card> getWastePile() {
        return Collections.unmodifiableList(wastePile);
    }

    public Deck getDeck() {
        return deck;
    }

    public void renamePlayer(String oldName, String newName) {
        for (Player player : players) {
            if (player.getName().equals(oldName)) {
                player.setName(newName);
                return;
            }
        }
    }

    public void removePlayer(String name) {
        int index = -1;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getName().equals(name)) {
                index = i;
                break;
            }
        }

        if (index != -1) {
            players.remove(index);
            if (index < currentPlayer) {
                currentPlayer--;
            } else if (index == currentPlayer) {
                if (currentPlayer >= players.size()) {
                    currentPlayer = 0;
                }
            }
        }
    }

    /**
     * Returns whether the game has finished. The last remaining player is the
     * loser of the game.
     */
    public boolean isFinished() {
        return players.size() <= 1;
    }

    public boolean isRearranging() {
        return playersReady.size() < players.size();
    }

    public boolean isPlaying() {
        return !isRearranging() && !isFinished();
    }

    public Set<Player> getPlayersReady() {
        return playersReady;
    }

    public void setPlayersReady(Set<Player> playersReady) {
        this.playersReady = playersReady;
    }

    public Card getTopCardFromDeck() {
        List<Card> cards = deck.getCards();
        if (cards.isEmpty()) {
            return null;
        }
        return cards.get(cards.size() - 1);
    }

    public void playCardFromDeck(Card card) {
        if (!isPlaying()) {
            throw new IllegalStateException("Game is not in PLAYING state.");
        }
        try {
            Card topCard = deck.dealCard();
            if (topCard != null && !topCard.equals(card)) {
                throw new IllegalArgumentException("Card is not the top card of the deck.");
            }

            Card top = wastePile.isEmpty() ? null : wastePile.get(wastePile.size() - 1);
            if (isPlayable(card.getRank(), top)) {
                wastePile.add(card);
                boolean burned = applySpecialRules(card.getRank());
                if (!burned) {
                    advanceTurn();
                }
            } else {
                getCurrentPlayer().getHand().add(card);
                pickUpWastePile();
            }
        } catch (CardDeckEmptyException e) {
            throw new IllegalStateException("no cards in deck", e);
        }
    }

    public Player getPlayer(String username) {
        return players.stream().filter(p -> p.getName().equals(username)).findFirst().orElse(null);
    }
}
