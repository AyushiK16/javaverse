
public class Recursion {

    static void countdown(int number) {
        if (number == 0) {
            return;
        }

        int next = number - 1;
        countdown(next);
    }

    public static void main(String[] args) {
        countdown(5);
    }
}
