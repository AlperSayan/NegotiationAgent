package geniusweb.sampleagent;
import geniusweb.actions.*;
import geniusweb.bidspace.AllBidsList;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Value;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.party.inform.*;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import tudelft.utilities.logging.Reporter;

import java.util.Map;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Level;



public class MyAgent extends DefaultParty {

    protected ProfileInterface profileint;
    private Bid lastReceivedBid = null; // we ignore all others
    private PartyId me;
    private Progress progress;
    private SimpleLinearOrdering estimatedProfile = null;
    private FrequencyOpponentModel frequencyOpponentModel = null;
    Graph graph = null;
    private final Random random = new Random();
    Settings settings;

    AllBidsList bidspace = null;
    HashMap<Bid, Double> my_bids = new HashMap<>();
    HashMap<Bid, Double> oppenentBids_and_utility = new HashMap<>();

    List<Bid> offered_bids = new ArrayList<>();
    List<Bid> ordered_bids = new ArrayList<>();
    List<Bid> ordered_opponent_bids = new ArrayList<>();
    List<Bid> opponent_bids = new ArrayList<>();
    List<Bid> satisfying_bids = new ArrayList<>();

    Bid best_bid = null;
    Bid worst_bid = null;
    Bid reservation_bid = null;
    Bid nash_bid = null;

    BigInteger index = BigInteger.ZERO;
    int counter = 1;
    int round = 0;
    int total_rounds = 0;
    int time_window = 0;
    int time_window_len = 7;

    double ratio;
    double time = 0;   // range = [0, 0.49], i tuned this parameter for 200 rounds
    double best_bid_imp = 0;
    double worst_bid_imp = 0;
    double most_urgent = 0;
    double how_much_to_concede = 0;
    double concede_upper_bound = 0;
    double concede_lower_bound = 0;
    double constant = 1;
    double nash_imp = 0;
    double nash_util = 0;

    boolean all_bids_generated = false;
    boolean converted_to_arraylist = false;
    boolean ordered_opponent_bids_created = false;


    public MyAgent() {
    }

    public MyAgent(Reporter reporter) {
        super(reporter); // for debugging
    }

    @Override
    public void notifyChange(Inform info) {
        try {
            if (info instanceof Settings) {

                settings = (Settings) info;
                this.profileint = ProfileConnectionFactory.create(settings.getProfile().getURI(), getReporter());
                this.me = settings.getID();
                this.progress = settings.getProgress();
                bidspace = new AllBidsList( profileint.getProfile().getDomain());
                frequencyOpponentModel = new FrequencyOpponentModel(profileint.getProfile().getDomain());
                estimatedProfile = new SimpleLinearOrdering(profileint.getProfile());
                graph = new Graph(profileint.getProfile().getDomain());
                graph.self_update(estimatedProfile.getBids());
                round = ((ProgressRounds) settings.getProgress()).getCurrentRound();
                total_rounds = ((ProgressRounds) settings.getProgress()).getTotalRounds();


            } else if (info instanceof ActionDone) {

                Action otheract = ((ActionDone) info).getAction();
                time = progress.get(System.currentTimeMillis());


                if (otheract instanceof Offer) {

                    lastReceivedBid = ((Offer) otheract).getBid();
                    frequencyOpponentModel = frequencyOpponentModel.update(lastReceivedBid);
                    oppenentBids_and_utility.put(lastReceivedBid, frequencyOpponentModel.getUtility(lastReceivedBid).doubleValue());
                    opponent_bids.add(lastReceivedBid);

                    if (reservation_bid == null){
                        reservation_bid = lastReceivedBid;
                    }
                    else {
                        if (graph.getImportance(reservation_bid) < graph.getImportance(lastReceivedBid)){
                            reservation_bid = lastReceivedBid;
                        }
                    }


                } else if (otheract instanceof Comparison) {
                    estimatedProfile = estimatedProfile.with(((Comparison) otheract).getBid(), ((Comparison) otheract).getBetter());
                    myTurn();

                }
            } else if (info instanceof YourTurn) {

                myTurn();
            } else if (info instanceof Finished) {

                getReporter().log(Level.INFO, "Final ourcome:" + info );
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle info", e);
        }
    }

    @Override
    public Capabilities getCapabilities() {
        return new Capabilities(new HashSet<>(Arrays.asList("SHAOP")));
    }

    @Override
    public String getDescription() {
        return "My agent, Offers bids.";
    }


    private void myTurn() throws IOException {
        round++;
        Action action = null;

        if (lastReceivedBid != null) {
            // then we do the action now, no need to ask user
            if (estimatedProfile.contains(lastReceivedBid)) {
                if (isGood(lastReceivedBid)) {
                    getReporter().log(Level.INFO, "\naccepted agent: Agent" + me);

                    action = new Accept(me, lastReceivedBid);
                }
            }
            else {
                action = new ElicitComparison(me, lastReceivedBid, estimatedProfile.getBids());
            }

            if (progress instanceof ProgressRounds) {
                progress = ((ProgressRounds) progress).advance();
            }
        }

        if (action == null) {
            action = offer_bid();
        }
        getConnection().send(action);
    }

    // bidding strategy
    private Offer offer_bid(){


        ratio = (double) round / (double) total_rounds;



        if (all_bids_generated && !converted_to_arraylist){
            ordered_bids.addAll(my_bids.keySet());
            converted_to_arraylist = true;
            get_best_bid();
            get_worst_bid();
            setHow_much_to_concede();
            getReporter().log(Level.SEVERE, "concede= " + how_much_to_concede);
            counter = -1;
        }

        if (ratio >= 0.1){
            oppenentBids_and_utility = sortByValue(oppenentBids_and_utility);
            ordered_opponent_bids.addAll(oppenentBids_and_utility.keySet());
            ordered_opponent_bids_created = true;
        }

        if (!all_bids_generated){
            getMaxBid();
            generate_bids();
        }

        // partially avoid getting preference profile estimation
        if (ratio <= 0.1){
            return offer_semi_randomBid();
        }
        else {
            counter++;


            if (ratio >= 0.489){ // last bid, tuned for 200 rounds
                return new Offer(me, reservation_bid);
                }

            // every 10 round change conceding amount
            if(ratio >= 0.10 && ratio <0.15){
                if (time_window == 0) {
                    setHow_much_to_concede();
                    satisfying_bids = get_threshold_and_bids(13, 0.2);
                    time_window++;
                }
                Offer offer = generate_best_bid_according_to_time(satisfying_bids);
                if (offer != null){
                    return offer;
                }
            }


            else if (ratio <= 0.15){
                if (time_window == 1) {
                    setHow_much_to_concede();
                    satisfying_bids = get_threshold_and_bids(3, 0.25);
                    time_window++;
                }


                Offer offer = generate_best_bid_according_to_time(satisfying_bids);
                if (offer != null){
                    return offer;
                }


            }
            else if (ratio <= 0.2){
                if (time_window == 2) {
                    setHow_much_to_concede();
                    satisfying_bids = get_threshold_and_bids(13, 0.25);
                    time_window++;
                }
                Offer offer = generate_best_bid_according_to_time(satisfying_bids);
                if (offer != null){
                    return offer;
                }

            }
            else if (ratio <= 0.25){
                if (time_window == 3) {
                    setHow_much_to_concede();
                    satisfying_bids = get_threshold_and_bids(13, 0.30);
                    time_window++;
                }

                Offer offer = generate_best_bid_according_to_time(satisfying_bids);
                if (offer != null){
                    return offer;
                }

            }
            else if(ratio <= 0.3){
                if (time_window == 4) {
                    setHow_much_to_concede();
                    satisfying_bids = get_threshold_and_bids(13, 0.35);
                    time_window++;
                }

                Offer offer = generate_best_bid_according_to_time(satisfying_bids);
                if (offer != null){
                    return offer;
                }

            }
            else if(ratio <= 0.35){
                if (time_window == 5) {
                    setHow_much_to_concede();
                    satisfying_bids = get_threshold_and_bids(13 , 0.40);
                    time_window++;
                }

                Offer offer = generate_best_bid_according_to_time(satisfying_bids);
                if (offer != null){
                    return offer;
                }

            }
            else if (ratio <= 0.4){
                if (time_window == 6) {
                    setHow_much_to_concede();
                    satisfying_bids = get_threshold_and_bids(15 ,0.45);
                    time_window++;
                }
                Offer offer = generate_best_bid_according_to_time(satisfying_bids);
                if (offer != null){
                    return offer;
                }

            }
            else if (ratio <= 0.486){
                if (time_window == 7) {
                    setHow_much_to_concede();
                    satisfying_bids = get_threshold_and_bids(15, 0.50);
                    time_window++;
                }
                Offer offer = generate_best_bid_according_to_time(satisfying_bids);
                if (offer != null){
                    return offer;
                }
            }

            return new Offer(me, ordered_bids.get(counter));
        }
    }

    // inputs size and opponent utility generates upper and lower bound and returns the bids that are in range
    public List<Bid> get_threshold_and_bids(int size, double opponent_utility){
        List<Bid> bids = new ArrayList<>();
        if (time_window == 0) {
            concede_upper_bound = best_bid_imp;
        }
        else {
            concede_upper_bound = concede_lower_bound;
        }
        concede_lower_bound = concede_upper_bound - constant * how_much_to_concede;
        while (true){
            bids = generate_satisfying_bid_list(concede_upper_bound, concede_lower_bound, opponent_utility);
            if (bids.size() >= size){
                break;
            }
            constant += 0.1;
            concede_lower_bound = concede_upper_bound - constant * how_much_to_concede;

        }
        constant = 1;
        return bids;
    }

    // generate satisfying bids
    public List<Bid> generate_satisfying_bid_list(double concede_upper_bound, double concede_lower_bound, double opponent_utility){
        List<Bid> bids_that_satisfy_condition = new ArrayList<>();

        for (Bid bid: ordered_bids){
            double current_bid_importance = graph.getImportance(bid);
            boolean already_offered = offered_bids.contains(bid);
            if((current_bid_importance <= concede_upper_bound)
                && (current_bid_importance >= concede_lower_bound)
                && (frequencyOpponentModel.getUtility(bid).doubleValue() >= opponent_utility)
                && !already_offered){
                bids_that_satisfy_condition.add(bid);
            }
        }

        return bids_that_satisfy_condition;
    }

    // generate offer from given bids send best bid for the opponent first
    public Offer generate_best_bid_according_to_time(List<Bid> bids_to_choose_from){

        Bid bid_to_be_offered = null;
        double max_opponent_util = 0;

        for (Bid bid : bids_to_choose_from){
            double current_opponent_util = frequencyOpponentModel.getUtility(bid).doubleValue();
            boolean already_offered = offered_bids.contains(bid);
            if((max_opponent_util < current_opponent_util)  && !already_offered){
                max_opponent_util = current_opponent_util;
                bid_to_be_offered = bid;
            }
        }


        if (bid_to_be_offered != null){
            //getReporter().log(Level.SEVERE,"utility of the bid offered to opponent=" + frequencyOpponentModel.getUtility(bid_to_be_offered));
            offered_bids.add(bid_to_be_offered);
            return new Offer(me, bid_to_be_offered);
        }

        return null;
    }

    // this must be set a time limit or a better data structure is needed for larger bid possibilities
    private void generate_bids(){

       while(my_bids.size() < bidspace.size().intValue()){
           Bid bid = bidspace.get(index);
           double importance = graph.getImportance(bidspace.get(index));
           my_bids.put(bid, importance);
           index = index.add(BigInteger.ONE);

       }


        if (my_bids.size()  == bidspace.size().intValue()) {
            all_bids_generated = true;
            my_bids = sortByValue(my_bids);
        }
    }


    // acceptance strategy
    private boolean isGood(Bid bid) {

        if (bid == null) {
            return false;
        }
        if (ratio < 0.45) {  // // until last window
            if (ordered_opponent_bids_created){
                if (ratio < 0.30){
                    return greater_utility_than_x_number_of_bids(12, bid);
                }
                else {
                    return greater_utility_than_x_number_of_bids(10, bid);
                }
            }
            else {
                return false;
            }

        }

        else if(ratio < 0.484 && ratio >= 0.45){ // for last window

                return greater_utility_than_x_number_of_bids(5, bid);

        }

        else { // a deal is better than no deal, only for the last 2 round
            return true;
        }
    }

    // returns if the utility of the last x offered bids are lower than the last received bid
    private boolean greater_utility_than_x_number_of_bids(int number_of_bids_to_consider, Bid last_received_bid){
        int ordered_oppent_bids_size = opponent_bids.size() - 2; // dont include last bid in comparison

        List<Bid> last_x_bids_send_by_opponent = opponent_bids.subList(ordered_oppent_bids_size - number_of_bids_to_consider, ordered_oppent_bids_size);
        boolean greater_than_all_last_x_bids = true;
        for (Bid current_bid : last_x_bids_send_by_opponent){
            if(graph.getImportance(current_bid) > graph.getImportance(last_received_bid)){
                greater_than_all_last_x_bids = false;
            }
        }
        return greater_than_all_last_x_bids && estimatedProfile.getUtility(last_received_bid).doubleValue() >= 0.8;

    }

    // returns max bid
    private void getMaxBid() {
        HashMap<String, Value> Values = new HashMap<>();

        for (Map.Entry<String, List<Node>> entry : this.graph.entrySet()) {

            Value value = entry.getValue().get(0).valueOfIssue;
            String issue = entry.getKey();
            Values.put(issue, value);
        }
        this.best_bid = new Bid(Values);
        this.most_urgent = this.graph.getImportance(this.best_bid);



    }

    // a simple sort method
    public static HashMap<Bid, Double> sortByValue(HashMap<Bid, Double> hm) {
        // Create a list from elements of HashMap
        List<Map.Entry<Bid, Double> > list =
                new LinkedList<Map.Entry<Bid, Double> >(hm.entrySet());

        // Sort the list
        Collections.sort(list, new Comparator<Map.Entry<Bid, Double> >() {
            public int compare(Map.Entry<Bid, Double> o1,
                               Map.Entry<Bid, Double> o2)
            {
                return (o2.getValue()).compareTo(o1.getValue());
            }
        });

        // put data from sorted list to hashmap
        HashMap<Bid, Double> temp = new LinkedHashMap<Bid, Double>();
        for (Map.Entry<Bid, Double> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }


    public void get_worst_bid(){
        worst_bid = ordered_bids.get(ordered_bids.size() -1);
        worst_bid_imp = graph.getImportance(worst_bid);
    }

    public void get_best_bid(){
        best_bid = ordered_bids.get(0);
        best_bid_imp = graph.getImportance(best_bid);

    }


    // parameters are tuned for party domain only
    public void setHow_much_to_concede(){
        getReporter().log(Level.SEVERE, "best bid importance= " + best_bid_imp);
        getReporter().log(Level.SEVERE, "worst bid importance= " + worst_bid_imp);
        if (ordered_opponent_bids_created) {
            double opponents_best_bid_imp = graph.getImportance(ordered_opponent_bids.get(0));
            nash_imp = (best_bid_imp + opponents_best_bid_imp)/ 2;
            getReporter().log(Level.SEVERE, "estimated nash imp= " + nash_imp);

            how_much_to_concede = nash_imp / (time_window_len + 3);
        }
        else {
            how_much_to_concede = (best_bid_imp - worst_bid_imp) / 20;
        }
        }

    public void nash_bid(){

        double distance = Math.abs(graph.getImportance(ordered_bids.get(0)) - nash_imp);

        for(Bid bid: ordered_bids){
            double imp = graph.getImportance(bid);
            double c_dist = Math.abs(imp - distance);
            if(c_dist < distance){
                nash_bid = bid;
                distance = c_dist;
            }

        }
    }
    // offer a random bid bigger than the threshold
    private Offer offer_semi_randomBid() {
        while (true) {
            long i = random.nextInt(bidspace.size().intValue());
            Bid bid = bidspace.get(BigInteger.valueOf(i));
            double threshold = graph.getImportance(best_bid) * 0.8;
            if (graph.getImportance(bid) >= threshold) {
                return new Offer(me, bid);
            }

        }
    }
}