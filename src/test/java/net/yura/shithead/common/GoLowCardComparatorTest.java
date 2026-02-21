package net.yura.shithead.common;

import net.yura.cardsengine.Card;
import net.yura.cardsengine.Rank;
import net.yura.cardsengine.Suit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoLowCardComparatorTest {

    @Test
    void testCompareGoLowEnabledForSeven() {
        GoLowCardComparator comparator = new GoLowCardComparator(true);
        Card topSeven = Card.getCardByRankSuit(Rank.SEVEN, Suit.CLUBS);
        comparator.setTopCard(topSeven);

        Card six = Card.getCardByRankSuit(Rank.SIX, Suit.HEARTS);
        Card eight = Card.getCardByRankSuit(Rank.EIGHT, Suit.SPADES);

        assertTrue(comparator.compare(six, topSeven) < 0, "6 should beat 7 under Go-Low");
        assertTrue(comparator.compare(eight, topSeven) > 0, "8 should lose against 7 under Go-Low");
    }
}