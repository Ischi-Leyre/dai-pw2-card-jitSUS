package ch.heigvd.dai.jitsus.game;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;


public class GameManager {
    private static final int maxRound = 13;
    private static final int deckSize = 36;

    GameManager() {}

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
        if (nP1.getFam() == nP2.getFam()) {
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
        switch ((nP1.getFam() - nP2.getFam()) % 4) {

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

    private CardSus[] createDeck() {
        CardSus[] deck = new CardSus[deckSize];

        for (int v = 1; v <= 9; v++)
            for (CardSus.family f : CardSus.family.values())
                deck[(v - 1) * f.ordinal()] = new CardSus(f, v);

        return deck;
    }

    private static CardSus[] shuffleDeck(CardSus[] deck) {
        // Mélange de Fisher-Yates, source pseudo code Wikipédia le 17.11.2025 22:15 fuseau Paris
        int j;
        for (int i = deckSize; i > 1; i--) {
            j = (int) (Math.random() * i);

            CardSus s = deck[i];
            deck[i] = deck[j];
            deck[j] = s;
        }

        return deck;
    }

    private int parseIn(BufferedReader in, BufferedWriter out) {
        String s;
        try {
            s = in.readLine();
        } catch (IOException e) {
            return -9;
        }
        switch(s) {
            case "help" :
                //todo
                return -2;
            case "surender" :
                //todo
                return -3;
            case "0" :
                return 0;
            case "1" :
                return 1;
            case "2" :
                return 2;
            case "3" :
                return 3;
            case "4" :
                return 4;
            default :
                return -1;
        }
    }

    public void run(Socket p1, Socket p2) {
        int[]  scores = new int[2];
        CardSus[] deck = createDeck();

        for (int round = 0; (round < maxRound) && (scores [0] < 7) && (scores[1] < 7); round++){
            deck = shuffleDeck(deck);
            try (BufferedReader in1 = new BufferedReader(new InputStreamReader(p1.getInputStream(),StandardCharsets.UTF_8));
                 BufferedReader in2 = new BufferedReader(new InputStreamReader(p2.getInputStream(),StandardCharsets.UTF_8));
                 BufferedWriter out1 = new BufferedWriter(new OutputStreamWriter(p1.getOutputStream(),StandardCharsets.UTF_8));
                 BufferedWriter out2 = new BufferedWriter(new OutputStreamWriter(p2.getOutputStream(),StandardCharsets.UTF_8));
            ) {
                // Announce hands
                for (int i = 0; i < 5; i++){
                    CardSus card = deck[i];
                    out1.write("card 1: " + String.valueOf(card.getVal()) + " of " + card.familyToString() + "\n");
                }
                for (int i = 5; i < 10; i++){
                    CardSus card = deck[i];
                    out2.write("card 1: " + String.valueOf(card.getVal()) + " of " + card.familyToString() + "\n");
                }

                // Selection of cards
                int nP1 = -1;
                int nP2 = -1;

                while (nP1 < 0 || nP2 < 0){
                    if (nP1 < 0) {
                        out1.write("Please select a card by entering it's number from 0 to 4.");
                        nP1 = parseIn(in1, out1);
                    }
                    if (nP2 < 0 ) {
                        out2.write("Please select a card by entering it's number from 0 to 4.");
                        nP2 = parseIn(in2, out2);
                    }
                }
                
                // Resolve the duel
                CardSus cardP1 = deck[nP1];
                CardSus cardP2 = deck[nP2];
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
                out1.write("You " + m1 + " against : " + String.valueOf(cardP2.getVal()) + " of " + cardP2.familyToString() + "\n");
                out2.write("You " + m2 + " against : " + String.valueOf(cardP1.getVal()) + " of " + cardP1.familyToString() + "\n");
            }
            catch (IOException e) {
                System.out.println("Exception : " + e);
            }
            // Releasing scores;
            /*
            * TODO
            */
        }
    }
}
