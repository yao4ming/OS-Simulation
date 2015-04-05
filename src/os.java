/**
 * Created by hp on 3/24/2015.
 */

public class os {

    public os() {
        System.out.println("hello world");
    }

    public static void startup(){
        new os();
    }


    //---------------------------------------------------INTERRUPT HANDLERS----------------------------------------------------------

    //invoked when job enters and handles placing that job in HDD queue
    //
    public static void Crint (int[] a, int []p){

        //store job info in job table
    }

    //job I/O is finished
    //p[5] current time
    public static void Dskint (int[] a, int []p){

    }

    //transfer of job from drum to memory completed
    //p[5] current time
    public static void Drmint (int[] a, int []p){

    }

    //intervalTimer == 0 job ran out of time
    //p[5] current time
    public static void Tro (int[] a, int []p) {
        //determine whether it was time-slice or finished
    }

    //system call by a process
    //p[5] current time
    public static void Svc (int[] a, int []p) {
        switch(a[0]){
            case 5: //terminate
                break;
            case 6: //I/O
                break;
            case 7: //block until pending I/O is complete
                break;
        }
    }
}
