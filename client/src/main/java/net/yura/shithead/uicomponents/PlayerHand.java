package net.yura.shithead.uicomponents;

import net.yura.mobile.gui.Font;
import net.yura.mobile.gui.Graphics2D;
import net.yura.mobile.gui.components.Component;
import net.yura.mobile.gui.layout.XULLoader;
import net.yura.shithead.common.Player;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.yura.shithead.common.ShitheadGame;

public class PlayerHand {

    private GameViewListener gameCommandListener;

    private final ShitheadGame game;
    final Player player;
    private List<UICard> uiCards = new ArrayList<UICard>();
    /**
     * the center of the hand
     */
    int x, y;
    boolean isLocalPlayer;
    private boolean isWaitingForInput = false;
    private static final int padding = XULLoader.adjustSizeToDensity(2);
    public static final int overlap = CardImageManager.cardHeight / 4;

    private static final Font font = new Font(javax.microedition.lcdui.Font.FACE_PROPORTIONAL, javax.microedition.lcdui.Font.STYLE_PLAIN, javax.microedition.lcdui.Font.SIZE_MEDIUM);

    public PlayerHand(ShitheadGame game, Player player, GameViewListener gameCommandListener) {
        this.game = game;
        this.player = player;
        this.gameCommandListener = gameCommandListener;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setWaitingForInput(boolean isCurrentPlayer) {
        this.isWaitingForInput = isCurrentPlayer;
    }

    public boolean isWaitingForInput() {
        return isWaitingForInput;
    }

    public void setCards(List<UICard> cards) {
        this.uiCards = cards;
    }

    public void layoutHand(List<UICard> cards, int yOffset, int maxWidth) {
        if (cards.isEmpty()) {
            return;
        }

        int horizontalSpacing = padding;
        int handWidth = (cards.size() * CardImageManager.cardWidth) + (horizontalSpacing * (cards.size() - 1));
        int startY = getYCardsStart() + yOffset;

        if (isLocalPlayer && handWidth > maxWidth) {

            // TODO a lot of this logic is repeated in the calculateNumRows method
            int cardsPerRow = (maxWidth + horizontalSpacing) / (CardImageManager.cardWidth + horizontalSpacing);
            if (cardsPerRow == 0) {
                cardsPerRow = 1;
            }
            int numRows = (int) Math.ceil((double) cards.size() / cardsPerRow);

            int cardIndex = 0;
            for (int row = 0; row < numRows; row++) {

                int cardsInThisRow = Math.min(cards.size() - cardIndex, cardsPerRow);
                int rowWidth = (cardsInThisRow * CardImageManager.cardWidth) + (horizontalSpacing * (cardsInThisRow - 1));
                int startX = -rowWidth / 2;

                for (int i = 0; i < cardsInThisRow; i++) {
                    UICard uiCard = cards.get(cardIndex++);
                    uiCard.setPosition(x + startX + i * (CardImageManager.cardWidth + horizontalSpacing), startY + row * CardImageManager.cardHeight / 2);
                }
            }
        }
        else {
            // for non local player we want to overlap large numbers of cards
            if (!isLocalPlayer) {
                if (handWidth > maxWidth) {
                    handWidth = maxWidth;
                    if (cards.size() > 1) {
                        horizontalSpacing = (maxWidth - cards.size() * CardImageManager.cardWidth) / (cards.size() - 1);
                    }
                }
            }
            int startX = -handWidth / 2;
            for (int i = 0; i < cards.size(); i++) {
                UICard uiCard = cards.get(i);
                uiCard.setPosition(x + startX + i * (CardImageManager.cardWidth + horizontalSpacing), startY);
            }
        }
    }

    public int calculateNumRows(List<UICard> cards, int maxWidth) {
        if (cards.isEmpty()) {
            return 0;
        }

        int spacing = padding;
        int handWidth = (cards.size() * CardImageManager.cardWidth) + (spacing * (cards.size() - 1));

        if (handWidth <= maxWidth) {
            return 1;
        }

        int cardsPerRow = (maxWidth + spacing) / (CardImageManager.cardWidth + spacing);
        if (cardsPerRow == 0) {
            cardsPerRow = 1;
        }
        return (int) Math.ceil((double) cards.size() / cardsPerRow);
    }

    public List<UICard> getUiCards() {
        return uiCards;
    }

    public List<UICard> getUiCards(CardLocation loc) {
        return getUiCards().stream().filter(uic -> uic.getLocation() == loc).collect(Collectors.toList());
    }

    public List<UICard> getSelectedUiCards() {
        return uiCards.stream().filter(UICard::isSelected).collect(Collectors.toList());
    }

    public int getYCardsStart() {
        return y - (CardImageManager.cardHeight + 2 * overlap) / 2;
    }

    public void paint(Graphics2D g, Component c) {

        int middleX = c.getWidth() / 2, middleY = c.getHeight() / 2;
        double angle = Math.atan2(y - middleY, x - middleX) - Math.PI/2;

        rotate(g, angle);

        int labelY = getYCardsStart();
        if (isWaitingForInput) {
            g.setColor(0xFF00FF00); // Green
            int arrowWidth = XULLoader.adjustSizeToDensity(30);
            int arrowHeight = XULLoader.adjustSizeToDensity(40);
            g.fillTriangle(x, labelY, x - arrowWidth, labelY - arrowHeight, x + arrowWidth, labelY - arrowHeight);
        }
        g.setFont(font);
        drawOutline(g, 0xFFFFFFFF, 0xFF000000, player.getName(), x -g.getFont().getWidth(player.getName()) / 2, labelY - g.getFont().getHeight());

        int currentY = 0;
        List<UICard> selected = new ArrayList<>();

        for (UICard card : uiCards) {

            int cardY = card.getY();
            if (cardY != currentY) {
                // we are done for this row, paint all selected cards now before moving onto next row
                drawSelectedOutline(g, c, selected);
                currentY = cardY;
            }

            if (card.isSelected()) {
                selected.add(card);
            }

            // if the card is currently in the process of moving, do NOT rotate it, let it fly free
            // TODO an alternative would be to have the rotation angle depend on the distance to the PlayerHand center
            boolean moving = card.moving();
            if (moving) {
                rotate(g, -angle);
            }
            card.paint(g, c);
            if (moving) {
                rotate(g, angle);
            }
        }

        drawSelectedOutline(g, c, selected);

        rotate(g, -angle);
    }

    private void drawSelectedOutline(Graphics2D g, Component c, List<UICard> selected) {
        for (UICard card : selected) {
            card.paintSelection(g, c);
        }

        selected.clear();
    }

    public static void drawOutline(Graphics2D g, int color, int outlineColor, String text, int x, int y) {
        g.setColor(outlineColor);
        int num = XULLoader.adjustSizeToDensity(1);
        g.drawString(text, x-num, y-num);
        g.drawString(text, x-num, y+num);
        g.drawString(text, x+num, y-num);
        g.drawString(text, x+num, y+num);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private void rotate(Graphics2D g, double angle) {
        if (!isLocalPlayer) {
            g.translate(x, y);
            g.getGraphics().rotate(angle);
            g.translate(-x, -y);
        }
    }

    public boolean processMouseEvent(int type, int x, int y, net.yura.mobile.gui.KeyEvent buttons) {
        if (type == net.yura.mobile.gui.DesktopPane.RELEASED) {
            for (int i = uiCards.size() - 1; i >= 0; i--) {
                UICard uiCard = uiCards.get(i);
                if (uiCard.contains(x, y)) {
                    if (game.isRearranging()) {
                        // when rearranging, only allow clicking on hand and up cards
                        if (uiCard.getLocation() == CardLocation.HAND || uiCard.getLocation() == CardLocation.UP_CARDS) {
                            List<UICard> selected = getSelectedUiCards();
                            if (selected.isEmpty() || uiCard == selected.get(0)) {
                                uiCard.toggleSelection();
                            }
                            else if (selected.size() == 1) {
                                selected.get(0).toggleSelection();
                                if (selected.get(0).getLocation() == uiCard.getLocation()) {
                                    uiCard.toggleSelection();
                                }
                                else if (selected.get(0) != uiCard) {
                                    gameCommandListener.swapCards(uiCard.getCard(), selected.get(0).getCard());
                                }
                            }
                            else {
                                System.out.println("too many cards selected???? " + selected);
                            }
                        }
                    }
                    else if (!game.isFinished()) {
                        Player player = game.getCurrentPlayer();
                        if (player.getHand().isEmpty() && player.getUpcards().isEmpty()) {
                            // TODO do we want to be able to pick the index of the downcard we play
                            gameCommandListener.playDowncard();
                        }
                        else if (uiCard.isPlayable()) {
                            // TODO if we have 2 of the same rank, we want to ONLY select it, so we can then play more then 1 at a time
                            CardLocation location = uiCard.getLocation();
                            long sameRank = uiCards.stream().filter(c -> c.getLocation() == location).filter(c -> c.getCard().getRank() == uiCard.getCard().getRank()).count();
                            if (sameRank == 1) {
                                uiCards.forEach(c -> c.setSelected(false)); // deselect all
                                gameCommandListener.playVisibleCard(uiCard.getLocation() == CardLocation.HAND, Collections.singletonList(uiCard.getCard()));
                            }
                            else {
                                // deselect all cards that are NOT this rank, when we have multiple of multiple ranks
                                uiCards.stream().filter(c -> c.isPlayable() && c.getCard().getRank() != uiCard.getCard().getRank()).forEach(c -> c.setSelected(false));
                                // if there is more then 1 then we just toggle the selection
                                uiCard.toggleSelection();
                                gameCommandListener.updateButton();
                            }
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }
}
