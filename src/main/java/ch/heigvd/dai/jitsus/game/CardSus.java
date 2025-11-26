package ch.heigvd.dai.jitsus.game;


public class CardSus {
    public enum family {
        Knife,
        Gun,
        Fist,
        Acid
    }

    private family fam;
    private int value;

    CardSus(family fam, int value) {
        if (value < 0 || value > 10) {
            System.out.println("Invalid value, correction in the correct range with the operator modulus");
            value = (value % 9) + 1;
        }
        this.fam = fam;
        this.value = value;
    }

    public String familyToString() {
        return switch (fam) {
            case Knife -> "\uD83D\uDD2A";
            case Gun -> "\uD83D\uDD2B";
            case Fist -> "\uD83D\uDC4A";
            case Acid -> "\uD83E\uDDEA";
        };
    }

    @Override
    public String toString() {
        return familyToString() + value;
    }

    public static void main(String[] args) {
        //CardSus[] deck = new CardSus[36];

        /*int index = 0;
        for (int v = 1; v <= 9; v++) {
            for (family f : family.values()) {
                deck[index++] = new CardSus(f, v);
            }
        }

        for (var card : deck)
            System.out.println(card);
        */
    }

    public int getFam(){
        return fam.ordinal();
    }

    public int getVal(){
        return value;
    }
}
