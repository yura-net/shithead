package net.yura.shithead.uicomponents;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.yura.lobby.mini.GameRenderer;
import net.yura.mobile.gui.Icon;
import net.yura.cardsengine.Card;
import net.yura.mobile.gui.layout.XULLoader;
import javax.microedition.lcdui.Image;
import net.yura.mobile.gui.Application;




public class CardImageManager {

    // at mdpi, the size of the card is half the image size
    // the images in the lib ARE kind of almost @2x, but they do not have it in the name
    public static final int cardWidth;
    public static final int cardHeight;

    static {
        String size = System.getProperty("display.size");
        if (Application.getPlatform() == Application.PLATFORM_ME4SE || "large".equals(size) || "xlarge".equals(size)) {
            cardWidth = XULLoader.adjustSizeToDensity(65);
            cardHeight = XULLoader.adjustSizeToDensity(120);
        }
        else {
            cardWidth = XULLoader.adjustSizeToDensity(44); // 44 is the SMALLEST to touch target according to apple
            cardHeight = XULLoader.adjustSizeToDensity(80); // 80
        }
    }

    private static final Map<String, Icon> cardImages = new HashMap<>();

    public static Icon getCardImage(Card card) {
        if (card == null) {
            return getCardBackImage();
        }
        String cardName = getCardName(card);
        if (!cardImages.containsKey(cardName)) {
            GameRenderer.ScaledIcon icon = new GameRenderer.ScaledIcon(cardWidth, cardHeight);

            // avoid any system scaling, load images just as bin resources
            try (InputStream in = Card.class.getResourceAsStream("/cards/" + cardName + ".gif")) {
                icon.setIcon(new Icon(Image.createImage(in)));
            }
            catch (Exception ex) {
                throw new RuntimeException(ex);
            }

            // old method, load through standard image loading system, system may autoscale
            //Icon cardIcon = new Icon("/cards/" + cardName + ".gif");
            //if (cardIcon.getIconWidth() <= 0 || cardIcon.getIconHeight() <= 0) {
            //    throw new IllegalStateException("invalid icon size for card " + cardName + " " + cardIcon);
            //}
            //icon.setIcon(cardIcon);
            cardImages.put(cardName, icon);
        }
        return cardImages.get(cardName);
    }

    public static Icon getCardBackImage() {
        String cardName = "back";
        if (!cardImages.containsKey(cardName)) {
            Icon icon = new CardBack(cardWidth, cardHeight);
            cardImages.put(cardName, icon);
        }
        return cardImages.get(cardName);
    }

    private static String getCardName(Card card) {
        return "" + card.getRank().toInt() + Character.toLowerCase(card.getSuit().toChar());
    }
}