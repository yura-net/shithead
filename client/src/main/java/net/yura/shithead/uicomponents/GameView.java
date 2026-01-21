package net.yura.shithead.uicomponents;

import net.yura.mobile.gui.Animation;
import net.yura.mobile.gui.DesktopPane;
import net.yura.mobile.gui.Font;
import net.yura.mobile.gui.Graphics2D;
import net.yura.mobile.gui.KeyEvent;
import net.yura.mobile.gui.components.Panel;
import net.yura.mobile.gui.layout.XULLoader;
import net.yura.shithead.common.Player;
import net.yura.shithead.common.ShitheadGame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.yura.cardsengine.Card;
import org.apache.commons.collections4.iterators.ReverseListIterator;

public class GameView extends Panel {

    private GameViewListener gameCommandListener;

    private ShitheadGame game;
    private String myUsername;
    private String title;
    private final List<UICard> deckAndWasteUICards = new ArrayList<UICard>();
    private final Map<Card, UICard> cardToUICard = new HashMap<>();
    private final Map<Player, PlayerHand> playerHands = new HashMap<Player, PlayerHand>();

    private final int padding = XULLoader.adjustSizeToDensity(2);
    private static final Font bigFont = new Font(javax.microedition.lcdui.Font.FACE_PROPORTIONAL, javax.microedition.lcdui.Font.STYLE_PLAIN, javax.microedition.lcdui.Font.SIZE_LARGE);

    public GameView() {

    }

    public void setTitle(String name) {
        title = name;
    }

    public void setGameCommandListener(GameViewListener gameCommandListener) {
        this.gameCommandListener = gameCommandListener;
    }

    public void setGame(ShitheadGame game, String playerID) {
        this.game = game;
        this.myUsername = playerID;
        layoutCards();
        repaint();
    }

    private PlayerHand getPlayerHand(String username) {
        return playerHands.get(game.getPlayer(username));
    }

    public List<Card> getSelectedCards() {
        PlayerHand hand = getPlayerHand(myUsername);
        if (hand == null) {
            return Collections.emptyList();
        }
        // TODO maybe use hand.getSelectedCards
        return hand.getUiCards().stream().filter(UICard::isSelected).map(UICard::getCard).collect(Collectors.toList());
    }

    public void clearSelectedCards() {
        PlayerHand hand = getPlayerHand(myUsername);
        if (hand != null) {
            hand.getUiCards().forEach(c -> c.setSelected(false));
        }
    }

    @Override
    public void doLayout() {
        super.doLayout();
        layoutCards();
    }

    @Override
    public void paintComponent(Graphics2D g) {
        super.paintComponent(g);
        for (PlayerHand hand : playerHands.values()) {
            hand.paint(g, this);
        }

        // paint deck and waste pile
        for (int i = 0; i < deckAndWasteUICards.size(); i++) {
            deckAndWasteUICards.get(i).paint(g, this);
        }

        if (game.isFinished()) {
            String text = "Game Over!";
            g.setFont(bigFont);
            PlayerHand.drawOutline(g, 0xFFFFFFFF, 0xFF000000, text, (getWidth() - bigFont.getWidth(text)) / 2, getHeight() / 2 - CardImageManager.cardHeight / 2 - bigFont.getHeight());
        }
        if (title != null) {
            g.setFont(bigFont);
            PlayerHand.drawOutline(g, 0xFFFFFFFF, 0xFF000000, title, (getWidth() - bigFont.getWidth(title)) / 2, padding);
        }

        // draw line that players are centered on
        //g.setColor(0xFFFF0000);
        //int w = getRadiusX() * 2;
        //int h = getRadiusY() * 2;
        //g.drawArc((getWidth() - w) / 2, (getHeight() - h) / 2,w,h,0,360);
    }

    /**
     * steps to lay out cards
     * 1) find as many unused UICards as possible from deck/waste and player
     * 2) go through each player and arrange cards using the available UICards as a source
     * 3) arrange deck and waste
     * 4) TODO the cards that are left should be animated off screen
     */
    private void layoutCards() {

        List<Player> players = game.getPlayers();
        int localPlayerIndex = -1;
        if (myUsername != null) {
            for (int i = 0; i < players.size(); i++) {
                if (myUsername.equals(players.get(i).getName())) {
                    localPlayerIndex = i;
                    break;
                }
            }
        }

        List<UICard> available = getUnusedCards(deckAndWasteUICards.stream().filter(c -> c.getLocation() == CardLocation.WASTE).collect(Collectors.toList()), game.getWastePile());
        available.addAll(getUnusedCards(deckAndWasteUICards.stream().filter(c -> c.getLocation() == CardLocation.DECK).collect(Collectors.toList()), (List<Card>)game.getDeck().getCards()));
        available.forEach(deckAndWasteUICards::remove);

        // we always want to keep a slot for the current player, regardless if they are still alive or not
        int numSlots = players.size() + (localPlayerIndex == -1 ? 1 : 0);

        double spaceForOthers = 1.6 * Math.PI; // (288 deg)

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            // makes sure this player is always at playerPosition 0, goes from 0 to players.size()-1
            int playerPosition = (i - localPlayerIndex + numSlots) % numSlots; // zero is very bottom
            // 360 deg is 2 * PI, ZERO DEGREES POINTS TO THE RIGHT!!!
            double angle = Math.PI/2 + ((Math.PI*2)-spaceForOthers)/2 + (spaceForOthers / numSlots * playerPosition);
            // WARNING! this calculation sends an incorrect value for position 0, but it is ignored by the method
            layoutPlayer(available, player, angle, i == localPlayerIndex);
        }
        // check if any player has got rid of all cards and left the game
        playerHands.entrySet().removeIf(h -> {
            boolean remove = !players.contains(h.getKey());
            if (remove) available.addAll(h.getValue().getUiCards());
            return remove;
        });


        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;

        // this is the lowest Y that is allowed for the deck and waste, they should not overlap this point as then they overlap the player
        int lowestY = getHeight();
        if (localPlayerIndex >= 0) {
            PlayerHand localPlayerhand = playerHands.get(game.getPlayers().get(localPlayerIndex));
            lowestY = localPlayerhand.getYCardsStart() - XULLoader.adjustSizeToDensity(20) - padding;
        }




        // Waste Pile
        int dip = XULLoader.adjustSizeToDensity(1);
        List<Card> wastePile = game.getWastePile();
        int wastePileSize = wastePile.size();
        if (wastePileSize > 0) {
            int stackHeightWaste = CardImageManager.cardHeight + (Math.min(wastePileSize, 3) - 1) * padding;
            int yStartWaste = Math.min(lowestY - stackHeightWaste, centerY - stackHeightWaste / 2);
            for (int i = 0; i < wastePileSize; i++) {
                Card card = wastePile.get(i);

                UICard wastePileCard = getUICard(available, Collections.emptyList(), card, CardLocation.WASTE, true);
                wastePileCard.setPosition(centerX + padding / 2, yStartWaste);
                if (i < (wastePileSize - 3)) {
                    yStartWaste = yStartWaste + dip;
                }
                else {
                    if ((i == (wastePileSize - 3) && card.getRank() == wastePile.get(wastePileSize - 1).getRank() && wastePile.get(wastePileSize - 2).getRank() == wastePile.get(wastePileSize - 1).getRank()) ||
                        (i == (wastePileSize - 2) && card.getRank() == wastePile.get(wastePileSize - 1).getRank())) {
                        yStartWaste = yStartWaste + CardImageManager.cardHeight / 10;
                    }
                    else {
                        yStartWaste = yStartWaste + padding;
                    }
                }
            }
        }

        // Deck (now that we have finished doing anything that may mess about with the deck, e.g. removing any cards)
        int deckCardsToShow = Math.min(game.getDeck().getCards().size(), 3);
        List<UICard> deckUICards = deckAndWasteUICards.stream().filter(c -> c.getLocation() == CardLocation.DECK).collect(Collectors.toList());

        if (deckCardsToShow > 0) {
            int stackHeightDeck = CardImageManager.cardHeight + (deckCardsToShow - 1) * padding;
            int yStartDeck = Math.min(lowestY - stackHeightDeck, centerY - stackHeightDeck / 2);

            // create deck cards
            while (deckUICards.size() < deckCardsToShow) {
                UICard newlyVisibleDeckCard = new UICard(null, CardLocation.DECK, false,
                        centerX - CardImageManager.cardWidth - padding / 2, yStartDeck);
                deckUICards.add(newlyVisibleDeckCard);
                deckAndWasteUICards.add(newlyVisibleDeckCard);
            }

            for (int i = 0; i < deckCardsToShow; i++) {
                UICard deckCard = deckUICards.get(i);
                deckCard.setPosition(centerX - CardImageManager.cardWidth - padding / 2, yStartDeck + i * padding);
            }
        }

        Animation.registerAnimated(this);
    }

    static List<UICard> getUnusedCards(List<UICard> source, List<Card> actual) {
        List<UICard> available = source.stream().filter(c -> c.getCard() != null && !actual.contains(c.getCard())).collect(Collectors.toList());
        // if we have removed unneeded cards, but we still have too many unknown cards, take out the extra unknown cards
        while (actual.size() < source.size() - available.size()) {
            available.add(source.stream().filter(c -> c.getCard() == null).findFirst().orElseThrow(() -> new IllegalStateException("no null cards found in: " + source)));
        }
        source.removeAll(available);
        return available;
    }

    /**
     * these 2 methods calculate the line on which the other players sit on
     */
    int getRadiusX() {
        return width / 2 - XULLoader.adjustSizeToDensity(15);
    }
    int getRadiusY() {
        return Math.max(
                // this is the minimal size, if we are less then this we will end up overlapping the deck and waste
                // any value less then 150, e.g. 145, ends up with the stack starting to overlap the player name label on iPhone SE (1st gen)
                XULLoader.adjustSizeToDensity(150),
                // this is best looking values, it needs to be big to allow for overlapping menubar
                height / 2 - XULLoader.adjustSizeToDensity(90));
    }

    private void layoutPlayer(List<UICard> available, Player player, double angle, boolean isLocalPlayer) {
        int centerX = width / 2;
        int centerY = height / 2;
        int radiusX = getRadiusX();
        int radiusY = getRadiusY();


        PlayerHand hand = playerHands.get(player);
        if (hand == null) {
            hand = new PlayerHand(game, player, gameCommandListener);
            playerHands.put(player, hand);
        }
        // this can get updated if the player resigns
        hand.isLocalPlayer = isLocalPlayer;
        hand.setWaitingForInput(game.isRearranging() ? !game.getPlayersReady().contains(player) : game.getCurrentPlayer() == player);

        Card top = game.getWastePile().isEmpty() ? null : game.getWastePile().get(game.getWastePile().size() - 1);

        // find as many available (not in use) UICards at the start, so we can reuse these when we need a new UICard
        List<UICard> oldDownUiCards = hand.getUiCards(CardLocation.DOWN_CARDS);
        available.addAll(0, getUnusedCards(oldDownUiCards, player.getDowncards()));
        List<UICard> oldUpUiCards = hand.getUiCards(CardLocation.UP_CARDS);
        available.addAll(0, getUnusedCards(oldUpUiCards, player.getUpcards()));
        List<UICard> oldHandUiCards = hand.getUiCards(CardLocation.HAND);
        available.addAll(0, getUnusedCards(oldHandUiCards, player.getHand()));

        List<UICard> downUiCards = player.getDowncards().stream()
                .map(card -> getUICard(available, oldDownUiCards, card, CardLocation.DOWN_CARDS, false))
                .collect(Collectors.toList());
        oldDownUiCards.removeAll(downUiCards);
        available.addAll(0, oldDownUiCards);

        List<UICard> handUiCards = player.getHand().stream()
                .map(card -> getUICard(available, oldHandUiCards, card, CardLocation.HAND, isLocalPlayer))
                .collect(Collectors.toList());
        oldHandUiCards.removeAll(handUiCards);
        available.addAll(0, oldHandUiCards);

        List<UICard> upUiCards = player.getUpcards().stream()
                .map(card -> getUICard(available, oldUpUiCards, card, CardLocation.UP_CARDS, true))
                .collect(Collectors.toList());
        oldUpUiCards.removeAll(upUiCards);
        available.addAll(0, oldUpUiCards);

        if (isLocalPlayer && hand.isWaitingForInput()) {
            boolean handActive = !player.getHand().isEmpty();
            boolean upcardsActive = game.isRearranging() || (player.getHand().isEmpty() && !player.getUpcards().isEmpty());

            if (handActive) {
                handUiCards.forEach(c -> c.setPlayable(game.isPlayable(c.getCard().getRank(), top)));
            }
            if (upcardsActive) {
                upUiCards.forEach(c -> c.setPlayable(game.isPlayable(c.getCard().getRank(), top)));
            }
        }

        hand.setCards(Stream.of(downUiCards, upUiCards, handUiCards)
                .flatMap(List::stream)
                .collect(Collectors.toList()));

        int threeCardsWidth = CardImageManager.cardWidth * 3 + XULLoader.adjustSizeToDensity(4); // 4 = 2 * PlayerHand.padding

        if (isLocalPlayer) {
            int maxWidth = Math.max(getWidth() - XULLoader.adjustSizeToDensity(66), threeCardsWidth);
            int handRows = hand.calculateNumRows(handUiCards, maxWidth);
            int upCardsOffset = PlayerHand.overlap;
            int handCardsOffset = game.getPlayersReady().contains(player) ? PlayerHand.overlap : CardImageManager.cardHeight / 2;
            int extraHandHeight = handRows > 1 ? (handRows - 1) * CardImageManager.cardHeight / 2 : 0;

            // starting at the bottom of the screen, find the top position
            hand.setPosition(centerX, height
                    -padding
                    -extraHandHeight
                    -CardImageManager.cardHeight
                    -upCardsOffset
                    -handCardsOffset
                    // add half a player area (as we will then remove this again in the layoutCard calculations
                    + (CardImageManager.cardHeight + 2 * PlayerHand.overlap) / 2);

            hand.layoutHand(downUiCards, 0, maxWidth);
            hand.layoutHand(upUiCards, upCardsOffset, maxWidth);
            hand.layoutHand(handUiCards, upCardsOffset + handCardsOffset, maxWidth);

        } else {
            int x = centerX + (int) (radiusX * Math.cos(angle));
            int y = centerY + (int) (radiusY * Math.sin(angle));
            hand.setPosition(x, y);
            hand.layoutHand(downUiCards, 0, threeCardsWidth);
            hand.layoutHand(upUiCards, PlayerHand.overlap, threeCardsWidth);
            hand.layoutHand(handUiCards, PlayerHand.overlap * 2, threeCardsWidth);
        }
    }

    private UICard getUICard(List<UICard> available, List<UICard> currentHandCardsAtLocation, Card card, CardLocation location, boolean isFaceUp) {
        UICard uiCard = cardToUICard.get(card);
        if (uiCard == null) {
            // if this card is unknown, maybe we can find an existing unknown card at this location, then just use that card
            if (card == null) {
                Optional<UICard> currentCard = currentHandCardsAtLocation.stream().filter(uic -> uic.getCard() == null).findFirst();
                if (currentCard.isPresent()) {
                    currentHandCardsAtLocation.remove(currentCard.get());
                    uiCard = currentCard.get();
                }
            }

            if (uiCard == null) {
                if (!available.isEmpty()) {
                    uiCard = available.stream().filter(c -> c.getCard() == card).findFirst().orElseGet(
                            () -> available.stream().filter(c -> c.getCard() == null).findFirst().orElse(null)
                    );
                    if (uiCard != null) {
                        available.remove(uiCard);
                    }
                }
                if (uiCard == null) {
                    uiCard = StreamSupport.stream(((Iterable<UICard>) () -> new ReverseListIterator<>(deckAndWasteUICards)).spliterator(), false)
                            .filter(c -> c.getLocation() == CardLocation.DECK).findFirst().orElse(null);
                    if (uiCard == null) {
                        // dealing a brand new card, this should ONLY happen at the start of the game
                        System.out.println("Dealing new card on table (ONLY AT START OF GAME)");
                        uiCard = new UICard();
                    }
                }
            }
        }
        updateCardInfo(uiCard, card, location, isFaceUp);
        return uiCard;
    }

    private void updateCardInfo(UICard uiCard, Card card, CardLocation location, boolean isFaceUp) {
        CardLocation oldLocation = uiCard.getLocation();
        if (oldLocation == CardLocation.DECK || oldLocation == CardLocation.WASTE) {
            deckAndWasteUICards.remove(uiCard);
        }
        // if we are moving from deck to waste, we WANT to remove it and add to make sure its at the top
        if (location == CardLocation.DECK || location == CardLocation.WASTE) {
            deckAndWasteUICards.add(uiCard);
        }
        if (card != uiCard.getCard()) {
            uiCard.setCard(card);
        }
        uiCard.setPlayable(false);
        uiCard.setLocation(location);
        uiCard.setFaceUp(isFaceUp);
        if (card != null) {
            cardToUICard.put(card, uiCard);
        }
    }

    @Override
    public void processMouseEvent(int type, int x, int y, KeyEvent buttons) {

        if (type == DesktopPane.RELEASED) {
            for (int i = deckAndWasteUICards.size() - 1; i >= 0; i--) {
                UICard uiCard = deckAndWasteUICards.get(i);
                // if user clicks on waste pile during our turn, this mean we should pick up the waste pile
                Player currentPlayer = game.getCurrentPlayer();
                if (uiCard.contains(x, y) && currentPlayer != null && currentPlayer.getName().equals(myUsername) && game.isPlaying()) {
                    if (uiCard.getLocation() == CardLocation.WASTE) {
                        clearSelectedCards();
                        gameCommandListener.pickUpWaste();
                        return;
                    }
                    else if (uiCard.getLocation() == CardLocation.DECK) {
                        clearSelectedCards();
                        gameCommandListener.playDeck();
                        return;
                    }
                }
            }
            for (PlayerHand hand : playerHands.values()) {
                // only allow clicking on my own cards
                if (hand.player.getName().equals(myUsername) && hand.isWaitingForInput()) {
                    if (hand.processMouseEvent(type, x, y, buttons)) {
                        repaint();
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void animate() {
        boolean more = false;
        for (int i = deckAndWasteUICards.size() - 1; i >= 0; i--) {
            UICard uiCard = deckAndWasteUICards.get(i);
            if (uiCard.animate()) {
                more = true;
            }
        }
        for (PlayerHand hand : playerHands.values()) {
            List<UICard> cards = hand.getUiCards();
            for (int i = cards.size() - 1; i >= 0; i--) {
                UICard uiCard = cards.get(i);
                if (uiCard.animate()) {
                    more = true;
                }
            }
        }
        if (more) {
            repaint();
            Animation.registerAnimated(this);
        }
    }
}
