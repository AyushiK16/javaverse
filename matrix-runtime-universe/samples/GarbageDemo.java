
class GarbageObject {
    int value;

    GarbageObject(int value) {
        this.value = value;
    }
}

public class GarbageDemo {
    public static void main(String[] args) {
        GarbageObject first = new GarbageObject(10);
        GarbageObject second = new GarbageObject(20);

        first = null;
        second = null;

        System.out.println("References removed");
    }
}
