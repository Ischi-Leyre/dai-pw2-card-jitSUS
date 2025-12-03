/**
 * @author Marc Ishi et Arnaut Leyre
**/
package ch.heigvd.dai.jitsus.game;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import ch.heigvd.dai.jitsus.protocol.ClientHandler;

public class GameManager implements Runnable {
    private static final int maxRound = 13;
    private static final int deckSize = 36;
    private final ClientHandler player1;
    private final ClientHandler player2; 
    private final String u1;
    private final String u2;

    private final BlockingQueue<QueuedMessage> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public GameManager(ClientHandler player1, ClientHandler player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.u1 = player1.getUsername();
        this.u2 = player2.getUsername();
    }

    /**
     * Receive messages from players
     *
     * @param from speaking player username
     * @param message speaking player message
     **/
    public void receive(String from, String message) {
        if (!running.get()) return;
        queue.offer(new QueuedMessage(from, message));
    }

    /**
     *  Take in two card descriptions and resolve their duel.
     *  Knife : 0, Fist : 1, Acid : 2, Gun : 3
     *
     * @param nP1,nP2 card played by each player
     * @return  res[0] : score player1, res[1] score player2
     **/
    private int[] duel(CardSus nP1, CardSus nP2) {
        int[] res = new int[2];

        // Equal-Type Victory
        if (nP1.famToInt() == nP2.famToInt()) {
            if (nP1.getVal() > nP2.getVal()) {
                res[0] = 1;
                res[1] = 0;
            }
            else {
                res[0] = 0;
                res[1] = 1;
            }
            return res;
        }

        // Aggressive Victory
        // the values of the types have been assigned such as:
        //      if type1 wins then (type1 - type2) % 4 = 3
        //      if type2 wins then (type1 - type2) % 4 = 1
        switch ((nP1.famToInt() - nP2.famToInt()) % 4) {
            case 1:
                res[0] = 0;
                res[1] = 2;
                return res;
            case 3:
                res[0] = 2;
                res[1] = 0;
                return res;
            default:
                break;
        }

        // Opposite Victory
        if (nP1.getVal() == nP2.getVal()) {
            // res is init to {0,0} which match the needed return value
            return res;
        }
        if (nP1.getVal() < nP2.getVal()) {
            res[0] = 0;
            res[1] = -1;
        }
        else {
            res[0] = -1;
            res[1] = 0;
        }
        return res;
    }

    /**
     * Creates a deck used in the match.
     *
     * @return a new CardSus deck of 36 cards.
     **/
    private CardSus[] createDeck() {
        CardSus[] deck = new CardSus[deckSize];

        for (int v = 1; v <= 9; v++)
            for (CardSus.family f : CardSus.family.values())
                deck[(v - 1) * f.ordinal()] = new CardSus(f, v);

        return deck;
    }

    /**
     * Shuffle the deck for the next draw.
     *
     * @return the shuffled CardSus deck.
     **/
    private static CardSus[] shuffleDeck(CardSus[] deck) {
        // Fisher-Yates mix, source Wikipedia pseudo code on 17.11.2025 22:15 Paris time
        int j;
        for (int i = deckSize; i > 1; i--) {
            j = (int) (Math.random() * i);

            CardSus s = deck[i];
            deck[i] = deck[j];
            deck[j] = s;
        }

        return deck;
    }

    /**
     * Lissen for player choice.
     *
     * @return The selected card or an negative value that can be interpreted.
     **/
    private int parseIn(String s) {
        switch(s.toUpperCase()) {
            case "1" :
                return 0;
            case "2" :
                return 1;
            case "3" :
                return 2;
            case "4" :
                return 3;
            case "5" :
                return 4;
            default :
                return -1;
        }
    }


    /**
     * Handle loser surrendering
     *
     * @param loser surrendering player
     * @param winner winning player by default
     **/
    private void surrender(ClientHandler loser, ClientHandler winner){
        message(winner, "MATCH_END You won the match with 7 points!");
        message(loser, "MATCH_END You lost the match with -7 points!");
        
        message(winner, "[GameManager] " + winner.handleMatchEnd(7));
        message(loser, "[GameManager] " + loser.handleMatchEnd(-7));
    }

    /**
     * send message to user and handle exeption if needed
     *
     * @param player player to contact
     * @param message message to relay
     **/
    private void message(ClientHandler player, String message){
        try {
            player.send(message);
        } catch (IOException e) {
            System.err.println("[GameManager] Error: " + e.getMessage());
        }
    }

    /**
     * Follow the game to it's conclusion.
     *
     **/
    public void run() {
        int[]  scores = new int[2];
        CardSus[] deck = createDeck();
        int round;

        for (round = 0; (round < maxRound) && (scores [0] < 7) && (scores[1] < 7); round++){
            deck = shuffleDeck(deck);
            // Announce hands
            for (int i = 0; i < 5; i++){
                CardSus card = deck[i];
                message(player1, "card " + i + ": " + String.valueOf(card.getVal()) + " of " + card.familyToString());
            }
            for (int i = 5; i < 10; i++){
                CardSus card = deck[i];
                message(player2, "card " + i + ": " + String.valueOf(card.getVal()) + " of " + card.familyToString());
            }

            // Selection of cards
            int nP1 = -1;
            int nP2 = -1;

            while (nP1 < 0 || nP2 < 0){
                QueuedMessage qm;
                try {
                    qm = queue.take();
                } catch (InterruptedException e) {
                    System.err.println("[GameManager] Error: " + e.getMessage());
                    return;
                }
               
                
                if (qm.from.equals(u1)) {
                    if (nP1 < 0) {
                        message(player1, "Please select a card by entering it's number from 0 to 4.");
                        nP1 = parseIn(qm.message);
                        if (qm.message.equals("SURRENDER")) {
                            surrender(player1, player2);
                            return;
                        }
                    }
                }
                if (qm.from.equals(u2)) {
                    if (nP2 < 0 ) {
                        message(player2, "Please select a card by entering it's number from 0 to 4.");
                        nP2 = parseIn(qm.message);
                        if (qm.message.equals("SURRENDER")) {
                            surrender(player2, player1);
                            return;
                        }
                    }
                }

            }
            
            // Resolve the duel
            CardSus cardP1 = deck[nP1];
            CardSus cardP2 = deck[nP2 + 5];
            String m1;
            String m2;

            int[] res = duel(cardP1, cardP2);
            scores[0] += res[0];
            scores[1] += res[1];
            if (res[0] == res[1]){
                m1 = "tied";
                m2 = "tied";
            }
            else {
                
                if (res[0] > res[1]) {
                    m1 = "won";
                    m2 = "lost";
                }
                else {
                    m1 = "lost";
                    m2 = "won";
                }
            }
            message(player1, "ROUND_END You " + m1 + " against : " + String.valueOf(cardP2.getVal()) + " of " + cardP2.familyToString() + "\nNow your score is " + scores[0]);
            message(player2, "ROUND_END You " + m2 + " against : " + String.valueOf(cardP1.getVal()) + " of " + cardP1.familyToString() + "\nNow your score is " + scores[1]);
        }

        // Releasing scores
        String m1;
        String m2;
        if ((round == 13) && (scores[0] < 7) && (scores[1] < 7)) {
            m1 = "lost";
            m2 = "lost";
        }
        else {
            if (scores[0] == scores[1]) {
                m1 = "tied";
                m2 = "tied";
            }
            else{
                if (scores[0] > scores[1]) {
                    m1 = "won";
                    m2 = "lost";
                }
                else {
                    m1 = "lost";
                    m2 = "won";
                }
            }
        }
        message(player1, "MATCH_END You " + m1 + " the match with " + scores[0] + " points!");
        message(player2, "MATCH_END You " + m2 + " the match with " + scores[1] + " points!");
        message(player1, "[GameManager] " + player1.handleMatchEnd(scores[0]));
        message(player2, "[GameManager] " + player2.handleMatchEnd(scores[1]));
    }

    // Internal class for communication
    private static class QueuedMessage {
        final String from;
        final String message;
        QueuedMessage(String from, String message) {
            this.from = from;
            this.message = message;
        }
    }
}
