
class Box {
    int value;

    Box(int value) {
        this.value = value;
    }
}

public class Objects1 {
    public static void main(String[] args) {
        Box first = new Box(10);
        Box second = new Box(20);

        first.value = 50;
        second = first;

        System.out.println(second.value);
    }
}
