
public class os {

    static int count = 0;
    static Job[] jobTable;
    static final int MAXJOBS = 50;
    static int[] interruptedJob;
    static float[] memoryTable;

    public static void startup(){

        //allocate memory for JobTable
        jobTable = new Job[MAXJOBS];
        for (int i = 0; i < MAXJOBS; i++) {
            jobTable[i] = new Job();
        }

        //allocate memory for interrupted jobs
        interruptedJob = new int[6];

        //allocate memory for free space table
        memoryTable = new float[100];


    }

    //encapsulate Job inside OS as member class
    public static class Job {
        int jobNum;
        int priority;
        int jobSize;
        int cpuTime;
        int currentTime;
    }

    public static void save(int[] p){
        System.arraycopy(p, 0, interruptedJob, 0, p.length);
    }


    //---------------------------------------------------INTERRUPT HANDLERS----------------------------------------------------------

    //job enters drum
    public static void Crint (int[] a, int []p){

        //store state of interrupted job
        save(p);

        jobTable[count].jobNum = p[1];
        jobTable[count].priority = p[2];
        jobTable[count].jobSize = p[3];
        jobTable[count].cpuTime = p[4];
        jobTable[count].currentTime = p[5];
        count++;

        //long-term scheduler(Decide which job to swap into memory)

        //swapper(swap job into memory)
        sos.siodrum(p[1], p[3], 98, 0);

        //set action and params for SOS
        a[0] = 1;

    }

    //finished I/O
    public static void Dskint (int[] a, int []p){

    }

    //finished swapping job
    public static void Drmint (int[] a, int []p){
        System.out.println("-----------------------------------------------------------------------------------------------");
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
