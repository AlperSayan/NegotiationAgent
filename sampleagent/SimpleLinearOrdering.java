package geniusweb.sampleagent;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.profile.DefaultPartialOrdering;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.UtilitySpace;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


public class SimpleLinearOrdering implements UtilitySpace {

    private final Domain domain;
    private final List<Bid> bids; // worst bid first, best bid last.

    SimpleLinearOrdering(Profile profile) {
        this(profile.getDomain(), getSortedBids(profile));
    }


    SimpleLinearOrdering(Domain domain, List<Bid> bids) {
        this.domain = domain;
        this.bids = bids;
    }


    public static List<Bid> getSortedBids(Profile profile) {
        if (!(profile instanceof DefaultPartialOrdering)) {
            throw new UnsupportedOperationException("Only DefaultPartialOrdering supported");
        }
        DefaultPartialOrdering prof = (DefaultPartialOrdering) profile;
        List<Bid> bidslist = prof.getBids();
        // NOTE sort defaults to ascending order, this is missing in docs.
        bidslist.sort((b1, b2) -> prof.isPreferredOrEqual(b1, b2) ? 1 : -1);

        return bidslist;
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Domain getDomain() {
        return domain;
    }

    @Override
    public Bid getReservationBid() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigDecimal getUtility(Bid bid) {
        if (bids.size() < 2 || !bids.contains(bid)) {
            return BigDecimal.ZERO;
        }
        // using 8 decimals, we have to pick something here
        return new BigDecimal(bids.indexOf(bid)).divide(new BigDecimal((bids.size() - 1)), 8, RoundingMode.HALF_UP);
    }


    public boolean contains(Bid bid) {
        return bids.contains(bid);
    }


    public List<Bid> getBids() {
        return Collections.unmodifiableList(bids);
    }


    public SimpleLinearOrdering with(Bid bid, List<Bid> worseBids) {
        int n = 0;
        while (n < bids.size() && worseBids.contains(bids.get(n))) {
            n++;
        }
        LinkedList<Bid> newbids = new LinkedList<Bid>(bids);
        newbids.add(n, bid);
        return new SimpleLinearOrdering(domain, newbids);
    }

}

