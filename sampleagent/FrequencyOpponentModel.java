package geniusweb.sampleagent;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.NumberValue;
import geniusweb.issuevalue.Value;
import geniusweb.profile.utilityspace.NumberValueSetUtilities;


public class FrequencyOpponentModel{

    private static final int DECIMALS = 4; // accuracy of our computations.
    private final Domain domain;
    private final Map<String, Map<Value, Integer>> bidFrequencies;
    private final BigDecimal totalBids;
    private static int serial = 1; // counter for auto name generation

    public FrequencyOpponentModel(Domain domain) {
        // map with empth hashmap for each issue.
        this(domain,
                domain.getIssues().stream().collect(
                        Collectors.toMap(iss -> iss, iss -> new HashMap<>())),
                BigDecimal.ZERO);
    }

    public BigDecimal getUtility(Bid bid) {
        String err = domain.isComplete(bid);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        if (totalBids == BigDecimal.ZERO) {
            return BigDecimal.ONE;
        }
        BigDecimal sum = BigDecimal.ZERO;
        // Assume all issues have equal weight.
        for (String issue : domain.getIssues()) {
            sum = sum.add(getFraction(issue, bid.getValue(issue)));
        }
        return sum.divide(new BigDecimal(bidFrequencies.size()), DECIMALS,
                BigDecimal.ROUND_HALF_UP);
    }

    public String getName() {
        return "FreqOppModel" + (serial++) + "For" + domain;
    }


    public BigDecimal getFraction(String issue, Value value) {
        if (totalBids == BigDecimal.ZERO) {
            return BigDecimal.ONE;
        }
        Integer freq = bidFrequencies.get(issue).get(value);
        if (freq == null) {
            freq = 0;
        }
        return new BigDecimal(freq).divide(totalBids, DECIMALS,
                BigDecimal.ROUND_HALF_UP);
    }


    public Domain getDomain() {
        return domain;
    }

    public FrequencyOpponentModel update(Bid bid) {
        String err = domain.isComplete(bid);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        Map<String, Map<Value, Integer>> newFreqs = cloneMap(bidFrequencies);
        for (String issue : domain.getIssues()) {
            Map<Value, Integer> freqs = newFreqs.get(issue);
            Value value = bid.getValue(issue);
            Integer oldfreq = freqs.get(value);
            if (oldfreq == null) {
                oldfreq = 0;
            }
            freqs.put(value, oldfreq + 1);
        }

        return new FrequencyOpponentModel(domain, newFreqs,
                totalBids.add(BigDecimal.ONE));
    }


    private FrequencyOpponentModel(Domain domain,
                                   Map<String, Map<Value, Integer>> freqs, BigDecimal total) {
        if (domain == null) {
            throw new NullPointerException("domain=null");
        }
        this.domain = domain;
        this.bidFrequencies = freqs;
        this.totalBids = total;
    }


    private static Map<String, Map<Value, Integer>> cloneMap(
            Map<String, Map<Value, Integer>> freqs) {
        Map<String, Map<Value, Integer>> map = new HashMap<>();
        for (String issue : freqs.keySet()) {
            map.put(issue, new HashMap<Value, Integer>(freqs.get(issue)));
        }
        return map;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((bidFrequencies == null) ? 0 : bidFrequencies.hashCode());
        result = prime * result + ((domain == null) ? 0 : domain.hashCode());
        result = prime * result
                + ((totalBids == null) ? 0 : totalBids.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FrequencyOpponentModel other = (FrequencyOpponentModel) obj;
        if (bidFrequencies == null) {
            if (other.bidFrequencies != null)
                return false;
        } else if (!bidFrequencies.equals(other.bidFrequencies))
            return false;
        if (domain == null) {
            if (other.domain != null)
                return false;
        } else if (!domain.equals(other.domain))
            return false;
        if (totalBids == null) {
            if (other.totalBids != null)
                return false;
        } else if (!totalBids.equals(other.totalBids))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "FrequencyOpponentModel[" + totalBids + "," + bidFrequencies
                + "]";
    }

    public Bid getReservationBid() {
        throw new UnsupportedOperationException();
    }

}
