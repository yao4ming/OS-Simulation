
public class os {


    public static void startup(){
        //init

    }


    //---------------------------------------------------INTERRUPT HANDLERS----------------------------------------------------------

    //job enters drum
    public static void Crint (int[] a, int []p){

        //store state of interrupted job

        //store job info in job table

        //long-term scheduler(Decide which job to swap into memory)

        //swapper(swap job into memory)

        //set action and params for SOS

    }

    //finished I/O
    public static void Dskint (int[] a, int []p){

    }

    //finished swapping job
    public static void Drmint (int[] a, int []p){

    }

    //job ran out of time
    public static void Tro (int[] a, int []p) {
        //determine whether it was time-slice or finished
    }

    //system call
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
