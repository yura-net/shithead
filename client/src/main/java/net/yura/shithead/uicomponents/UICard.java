package net.yura.shithead.uicomponents;

import net.yura.cardsengine.Card;
import net.yura.cardsengine.Rank;
import net.yura.mobile.gui.DesktopPane;
import net.yura.mobile.gui.Font;
import net.yura.mobile.gui.Graphics2D;
import net.yura.mobile.gui.Icon;
import net.yura.mobile.gui.border.Border;
import net.yura.mobile.gui.border.LineBorder;
import net.yura.mobile.gui.components.Component;
import net.yura.mobile.gui.layout.XULLoader;

public class UICard {

    private Card card;
    private CardLocation location;
    private boolean faceUp;
    private int x, y;
    private int targetX, targetY;
    /**
     * Allows for selecting multiple cards with red border when multiple cards can be played
     */
    private boolean selected;
    /**
     * used by the UI to draw a yellow border to indicate this card can be played right now
     */
    private boolean playable;

    private static final Border selectionBorder = new LineBorder(0xFF87CEEB, XULLoader.adjustSizeToDensity(4));
    private static final Border playableBorder = new LineBorder(0xFFFFBF00, XULLoader.adjustSizeToDensity(2));

    private static final Font font = new Font(javax.microedition.lcdui.Font.FACE_PROPORTIONAL, javax.microedition.lcdui.Font.STYLE_PLAIN, javax.microedition.lcdui.Font.SIZE_SMALL);

    /**
     * used when dealing a brand new card from a pack (comes in from off-screen)
     */
    public UICard() {
    }

    /**
     * used for creating a UICard for a card already existing on the table (in deck)
     */
    public UICard(Card card, CardLocation location, boolean faceUp, int x, int y) {
        this.card = card;
        this.location = location;
        this.faceUp = faceUp;
        this.x = x;
        this.y = y;
    }

    public void setPosition(int x, int y) {
        this.targetX = x;
        this.targetY = y;
    }

    public void paint(Graphics2D g, Component c) {
        Icon icon;
        String text = null;
        if (faceUp && card != null) {
            icon = CardImageManager.getCardImage(card);

            if (Rank.TEN.equals(card.getRank())) {
                text = "Burn";
            }
            else if (Rank.TWO.equals(card.getRank())) {
                text = "Reset";
            }
        }
        else {
            icon = CardImageManager.getCardBackImage();
        }

        icon.paintIcon(c, g, x, y);

        if (text != null) {
            g.setColor(0xFF000000);
            g.setFont(font);
            g.drawString(text, x + (icon.getIconWidth() - font.getWidth(text)) / 2, y + icon.getIconHeight() - font.getHeight());
        }

        if (!selected && playable) {
            g.translate(x, y);
            playableBorder.paintBorder(c, g, icon.getIconWidth(), icon.getIconHeight());
            g.translate(-x, -y);
        }
    }

    /**
     * as selection overlaps the cards around it, we want to paint this last
     */
    public void paintSelection(Graphics2D g, Component c) {
        g.translate(x, y);
        selectionBorder.paintBorder(c, g, CardImageManager.cardWidth, CardImageManager.cardHeight);
        g.translate(-x, -y);
    }

    public Card getCard() {
        return card;
    }

    public CardLocation getLocation() {
        return location;
    }

    public void setLocation(CardLocation location) {
        this.location = location;
    }

    public void setFaceUp(boolean faceUp) {
        this.faceUp = faceUp;
    }

    public boolean isSelected() {
        return selected;
    }

    public boolean isPlayable() {
        return playable;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void toggleSelection() {
        selected = !selected;
    }

    public void setPlayable(boolean isPlayable) {
        this.playable = isPlayable;
    }

    public boolean contains(int px, int py) {
        return px >= x && px <= x + CardImageManager.cardWidth &&
               py >= y && py <= y + CardImageManager.cardHeight;
    }

    public boolean moving() {
        return x != targetX || y != targetY;
    }
    public int getY() {
        return y;
    }

    public boolean animate() {
        boolean more = false;
        if (moving()) {
            double[] pos = moveToward(x, y, targetX, targetY);
            x = (int)pos[0];
            y = (int)pos[1];

            more = true;
        }

        return more;
    }

    public static double[] moveToward(double x, double y, double targetX, double targetY) {

        double dx = targetX - x;
        double dy = targetY - y;

        double distance = Math.sqrt(dx * dx + dy * dy);

        int fullDistance = Math.min(DesktopPane.getDesktopPane().getWidth(), DesktopPane.getDesktopPane().getHeight());

        // Already at target
        if (distance == 0) {
            return new double[]{x, y};
        }

        // this is not really the proper way to use Easings, but it also works in converting our distance to speed
        // in an ideal world, we would be converting TIME to DISTANCE
        double speed = Easings.easeOutQuint(Math.min(distance / fullDistance, 1.0));

        // Step size logic
        double maxStep = speed * (fullDistance / 20.0);
        double minStep = 1.0;

        // Slow down as we get close
        double step = Math.min(maxStep, distance);
        if (step < minStep) {
            step = minStep;
        }

        // Never overshoot
        if (step > distance) {
            step = distance;
        }

        // Normalize direction
        double nx = dx / distance;
        double ny = dy / distance;

        // Move
        x += nx * step;
        y += ny * step;

        return new double[]{Math.round(x), Math.round(y)};
    }

    public void setCard(Card card) {
        if (this.card != null) {
            throw new IllegalStateException("already has card " + this.card);
        }
        this.card = card;
    }

    @Override
    public String toString() {
        return "UICard-" + card;
    }
}
