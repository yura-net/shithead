package net.yura.shithead.common;

import net.yura.cardsengine.Card;
import net.yura.cardsengine.Rank;
import java.util.Comparator;

public class GoLowCardComparator implements Comparator<Card> {

    private final boolean goLowEnabled;
    private volatile Card topCard;

    public GoLowCardComparator(boolean goLowEnabled) {
        this.goLowEnabled = goLowEnabled;
    }

    public void setTopCard(Card topCard) {
        this.topCard = topCard;
    }

    @Override
    public int compare(Card c1, Card c2) {
        int rank1 = normalizedRank(c1);
        int rank2 = normalizedRank(c2);

        if (goLowEnabled && topCard != null && topCard.getRank() == Rank.SEVEN) {
            // Go-Low: lower rank is "better" → lower rank should come first
            int cmp = Integer.compare(rank1, rank2);
            if (cmp != 0) return cmp;
            return Integer.compare(c1.getSuit().toInt(), c2.getSuit().toInt());
        }

        int cmp = Integer.compare(rank1, rank2);
        if (cmp != 0) return cmp;
        return Integer.compare(c1.getSuit().toInt(), c2.getSuit().toInt());
    }

    /** Returns the card's rank as an integer, treating Ace as high (14). */
    private int normalizedRank(Card card) {
        int rank = card.getRank().toInt();
        return rank == 1 ? 14 : rank;
    }
}