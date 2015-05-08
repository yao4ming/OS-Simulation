import com.google.common.collect.Iterables;

import java.util.*;

public class os {

    //job management
    static int tableIndex = 0;
    static Job[] jobTable;
    static final int MAXJOBS = 50;
    static final int MAXMEMORY = 100;

    //memory management
    static int[] memory;
    static List<Job> jobsInMemory;
    static Map<Integer, Integer> freeSpace;


    //I/O Management
    static LinkedList<Integer> diskQueue;
    static boolean diskbusy = false;

    //HDD Management
    static List<Integer> drumQueue;
    static boolean drumbusy = false;
    static boolean swappingIN = false;
    static boolean waitingOnDrum = false;

    public static class Job {

        public Job(int num, int priority, int size, int maxCPUTime, int drumArrival, int timeSlice) {
            this.num = num;
            this.priority = priority;
            this.size = size;
            this.maxCPUTime = maxCPUTime;
            this.age = maxCPUTime;
            this.drumArrival = drumArrival;
            this.timeSlice = timeSlice;
            this.CPUTimeLeft = maxCPUTime;
        }

        int num;
        int priority;
        int size;
        int maxCPUTime;
        int drumArrival;
        int CPUArrival;
        int startAddr;
        int timeSlice;
        int CPU_Usage;
        int CPUTimeLeft;
        int RR;
        int age;
        boolean inTransit;
        boolean awaitingIO;
        boolean doingIO;
        boolean blocked;
        boolean inCore;
        boolean running;
        boolean kill;
    }

    public static void startup() {

        //JobTable
        jobTable = new Job[MAXJOBS];
        for (int i = 0; i < MAXJOBS; i++) jobTable[i] = null;

        //memory management
        memory = new int[MAXMEMORY];
        jobsInMemory = new ArrayList<Job>();
        freeSpace = new HashMap<Integer, Integer>();
        freeSpace.put(0, MAXMEMORY);


        //I/O management
        diskQueue = new LinkedList<Integer>();

        //drum management
        drumQueue = new ArrayList<Integer>();

    }

    //-------------------------------------------------------SUBROUTINES-----------------------------------------------------------

    //update cpu-usage, cpu timeleft of interrupted job
    public static void saveJob(Job job, int currentTime) {

        if (job != null && job.running) {
            //update cpu usage
            job.CPU_Usage = job.CPU_Usage + (currentTime - job.CPUArrival);

            //update cpu time left
            job.CPUTimeLeft = job.CPUTimeLeft - (currentTime - job.CPUArrival);

            //update time-slice
            if (job.timeSlice > job.CPUTimeLeft) job.timeSlice = job.CPUTimeLeft;

            //take job out of cpu
            job.running = false;
        }else {
            System.out.println("No job interrupted");
        }

    }

    //returns the start addr for job
    //return -1, job doesn't fit into freespace
    public static int firstFit(int jobNum, int jobSize) {

        //traverse through freeSpace table
        for(Integer freeAddr : freeSpace.keySet()) {

            //job fits into freeSpace
            if(jobSize <= freeSpace.get(freeAddr)) {

                //allocate job into memory
                for (int j = freeAddr; j < freeAddr + jobSize; j++) memory[j] = jobNum;

                //new freespace
                freeSpace.put(freeAddr+jobSize, freeSpace.get(freeAddr) - jobSize);   //increase freeSpaceAddr, decrease freeSpaceSize

                //remove old freespace
                freeSpace.remove(freeAddr);

                printMemory();

                printFreeSpace();

                return freeAddr;
            }
        }

        return -1;
    }

    //return start addr for job
    //return -1, job doesn't fit into freespace
    public static int bestFit(int jobNum, int jobSize) {

        int startAddr = -1, min = 100;
        boolean spaceFound = false;
        for (Integer addr : freeSpace.keySet()) {

            //job fit into freespace
            if (jobSize <= freeSpace.get(addr)) {

                //smallest freespace that fits
                if (freeSpace.get(addr) - jobSize < min) {
                    min = freeSpace.get(addr) - jobSize;
                    startAddr = addr;
                    spaceFound = true;
                }
            }
        }

        if (spaceFound) {

            //allocate job into memory
            for (int i = startAddr; i < startAddr + jobSize; i++) memory[i] = jobNum;

            //new freespace
            int space = freeSpace.get(startAddr) - jobSize;
            if (space > 0) freeSpace.put(startAddr + jobSize, space);

            //remove old freespace
            freeSpace.remove(startAddr);

            printMemory();

            //printFreeSpace();
        }

        return startAddr;
    }

    //use fitting algorithm to find memory location for job
    //swap job into memory
    public static void swapIn(int jobNum) {

        Job job = searchJobTable(jobNum);

        //find space in memory for job
        job.startAddr = bestFit(job.num, job.size);

        //if valid addr, swap job into memory
        if(job.startAddr >= 0) sos.siodrum(jobNum, job.size, job.startAddr, 0);
        else System.out.println();

        job.inTransit = true;
        drumbusy = true;
        swappingIN = true;
    }

    //swap job out to drumQueue
    public static void swapOut(int jobNum) {

        Job job = searchJobTable(jobNum);

        int[] addrs = updateMemory(job);
        updateFreeSpace(addrs[0], addrs[1], job.size);

        sos.siodrum(job.num, job.size, job.startAddr, 1);

        job.inTransit = true;
        job.inCore = false;
        job.RR = 0;
        drumbusy = true;
        System.out.println();
    }

    //add jobs with maxcputime < 1000 to drumQueue
    //jobs with maxcputime > 1000 wait longer (lower priority)
    public static void priorityScheduler() {

        for (Job job : jobTable) {

            if (job != null && !job.inCore && !job.inTransit && !drumQueue.contains(job.num)) {
                if (freeSpace.containsValue(100)) {
                    drumQueue.add(job.num);
                }
                else if (job.age < 1000) {
                    drumQueue.add(job.num);
                }
                else if (job.age < 5000) {
                    job.age -= 10;
                }
                else if (job.age < 10000) {
                    job.age -= 10;
                }
                else if (job.age < 50000) {
                    job.age -= 10;
                }
                else {
                    job.age -= 10;
                }
            }
        }
    }

    //add job to diskQueue
    public static void requestIO(Job job) {

        //add I/O request to diskQueue
        diskQueue.add(job.num);
        job.awaitingIO = true;

        System.out.println("DiskQueue: " + diskQueue);
        System.out.println();

    }

    //swap I/O job back into memory
    public static void swapJobBackIn(Job top) {

        //find space for job
        for (Integer addr : freeSpace.keySet()) {

            //job fits into freespace
            if (top.size < freeSpace.get(addr)) {
                swapIn(top.num);
                return;
            }
        }

        //swap out jobs to make space
        Job swapoutJob = swappableJob();
        if (swapoutJob != null) {
            swapOut(swapoutJob.num);
        }

    }

    //returns job that is able to be swapped
    public static Job swappableJob() {

        //find job to swap out
        for (Job job : jobsInMemory) {
            if (!job.doingIO) return job;
        }
        return null;
    }

    //start I/O for first job in queue, if not incore swap in
    public static void startIO() {

        //check if disk is busy
        if (!diskbusy && diskQueue.peek() != null) {

            Job top = searchJobTable(diskQueue.peek());

            //if job not incore, swap job in
            if (!top.inCore) {
                if (!drumbusy) swapJobBackIn(top);
            }

            if (top.inCore) {
                sos.siodisk(top.num);
                top.doingIO = true;
                top.awaitingIO = false;
                diskQueue.poll();
                diskbusy = true;
            }
        }
    }

    //block job
    public static void blockJob(Job job) {

        job.blocked = true;
        System.out.println("Block Job " + job.num);

        //unblock if job not in diskQueue and not doing I/O
        if (!job.awaitingIO && !job.doingIO) {
            unblockJob(job);
            System.out.println("Unblock job b/c it is not waiting for I/O");
        }

    }

    //unblock job
    public static void unblockJob(Job job) {
        job.blocked = false;
        System.out.println("Unblock Job " + job.num);
    }

    //add jobs to drumQueue using priority scheduler
    //swap jobs into memory if enough space
    //swap blocked jobs out of memory if drumQueue > 20
    public static void swapJob() {

        //long term scheduler
        priorityScheduler();

        if (!drumbusy) {

            //traverse drumQueue for job to swap into memory
            for (int jobNum : drumQueue) {

                Job job = searchJobTable(jobNum);

                //traverse freespace address
                for (Integer addr : freeSpace.keySet()) {

                    //job fits into freespace
                    if (job.size < freeSpace.get(addr)) {
                        System.out.println(drumQueue);
                        swapIn(jobNum);
                        return;
                    }
                }
            }

            //attempt to swap jobs out if drumQueue is too big
            if (drumQueue.size() > 20) {

                //traverse diskQueue starting in the back
                ListIterator<Integer> diskIter = diskQueue.listIterator(diskQueue.size());

                while (diskIter.hasPrevious()) {
                    Job j = searchJobTable(diskIter.previous());

                    //swap out job if incore
                    if (j.inCore && j.num != diskQueue.peek()) {
                        swapOut(j.num);
                        System.out.println("Swap out job " + j.num);
                        printJobTable();
                        break;
                    }
                }
            }
        }
    }

    public static boolean isRunnable(Job job) {
        if (!job.blocked && !job.kill && !job.inTransit && job.inCore) return true;
        else return false;
    }

    public static boolean isRRFinish() {
        for (Job job : jobsInMemory) {
            if (isRunnable(job) && job.RR == 0) return false;
        }
        return true;
    }

    public static void resetRR() {
        for (Job job : jobsInMemory) {
            if (job.RR == 1) job.RR = 0;
        }
    }

    //return a job to run using RR algorithm
    public static Job roundRobin() {

        //check if all jobs ran RR
        boolean RRfinish = isRRFinish();
        if (RRfinish) resetRR();

        //find job in runnable memory with RR = 0
        for (Job job : jobsInMemory) {
            //return job to run RR
            if (isRunnable(job) && job.RR == 0) {
                job.RR = 1;
                return job;
            }
        }
        return null;

    }

    //a = 1, if no job to run
    //a = 2, set job to run, size, cpuArrival, timeslice
    public static void runJob(int[] a, int[] p) {

        //cpu idle
        a[0] = 1;

        //scheduler
        Job job = roundRobin();

        //printJobTable();

        if (job != null) {
            a[0] = 2;
            job.running = true;
            job.CPUArrival = p[5];
            p[2] = job.startAddr;
            p[3] = job.size;
            p[4] = job.timeSlice;
            System.out.println("Running job " + job.num);
        }
        else {
            System.out.println("No job to run");
        }

    }

    //returns job currently running on cpu
    public static Job currentJob() {
        for (Job job : jobTable) {
            if (job != null && job.running) {
                return job;
            }
        }
        return null;
    }

    //returns job in transmission between drum -> memory, memory -> drum
    public static Job jobInTransit() {
        for (Job job  : jobTable) {
            if (job != null && job.inTransit) {
                return job;
            }
        }
        return null;
    }

    //returns Job doing I/O
    public static Job jobInIO() {
        //find job doing I/O
        for (Job job : jobTable) {
            if (job != null && job.doingIO) {
                return job;
            }
        }
        return null;
    }


    //removes job from memory
    //returns start and end address of job used to update freespace
    public static int[] updateMemory(Job job) {

        int startAddr = 0, endAddr = 0;

        //iterate through memory to find job
        for (int i = 0; i < MAXMEMORY; i++) {
            //found start of job
            if (job.num == memory[i]) {
                startAddr = i;
                endAddr = startAddr + job.size;
                //remove job from memory
                for (int j = startAddr; j < endAddr; j++) {
                    memory[j] = 0;
                }
                break;
            }
        }

        int[] addrs = {startAddr, endAddr};
        return addrs;
    }

    public static void updateFreeSpace(int startAddr, int endAddr, int jobSize) {
        //freespace map before update
        //printFreeSpace();

        boolean after = false, before = false;
        int freeAddr = -1, freeSize = -1, newSize = -1;
        int spaceBeforeJob = -1, spaceAfterJob = -1;
        for (Integer key : freeSpace.keySet()) {

            freeAddr = key;
            freeSize = freeSpace.get(key);

            if (freeAddr + freeSize == startAddr) {     //job terminated is after freespace
                after = true;
                spaceBeforeJob = freeAddr;
            }
            else if (freeAddr == endAddr) {       //job terminated is before freespace
                before = true;
                spaceAfterJob = freeAddr;
            }
        }

        if (before && after) {
            newSize = freeSpace.get(spaceBeforeJob) + jobSize + freeSpace.get(spaceAfterJob);
            freeSpace.remove(spaceBeforeJob);
            freeSpace.remove(spaceAfterJob);
            freeSpace.put(spaceBeforeJob, newSize);
        }
        else if (before) {
            newSize = freeSpace.get(spaceAfterJob) + jobSize;
            freeSpace.remove(spaceAfterJob);
            freeSpace.put(startAddr, newSize);
        }
        else if (after) {
            newSize = freeSpace.get(spaceBeforeJob) + jobSize;
            freeSpace.remove(spaceBeforeJob);
            freeSpace.put(spaceBeforeJob, newSize);
        }
        else {
            freeSpace.put(startAddr, jobSize);
        }

        //freespace map after update
        //printFreeSpace();

    }

    //remove job from jobtable
    public static void updateJobTable(int jobNum) {
        for (int i = 0; i < MAXJOBS; i++) {
            if (jobTable[i] != null && jobTable[i].num == jobNum) {
                jobTable[i] = null;
                break;
            }
        }

        printJobTable();

        System.out.println();
    }

    //set tableindex at position of terminated job in jobtable
    public static void setTableIndex() {

        for (int i = 0; i < MAXJOBS; i++) {
            if (jobTable[i] == null) {
                tableIndex = i;
                break;
            }
        }
    }

    public static void terminate(Job job) {

        System.out.println("Terminate job " + job.num + " of size " + job.size);

        int[] addrs = updateMemory(job);

        jobsInMemory.remove(job);

        printMemory();

        updateFreeSpace(addrs[0], addrs[1], job.size);

        updateJobTable(job.num);



    }

    public static Job searchJobTable(int jobNum) {
        for (Job job : jobTable) {
            if (job != null && job.num == jobNum) {
                return job;
            }

        }
        return null;
    }

    public static void printFreeSpace() {

        System.out.println("freespace");
        for (Integer key : freeSpace.keySet()) {
            System.out.println(key + " " + freeSpace.get(key));
        }
    }

    public static void printJobTable() {
        System.out.println("Jobtable");
        int i = 1;
        for (Job job : jobTable) {
            if(job != null && job.inCore) {
                System.out.println("Job Num " + job.num + " in memory " + " age " + job.age + "\t" + i);
                i++;
            }
            else if (job != null) {
                System.out.println("Job num " + job.num + " priority " + job.priority + " age " + job.age + "\t" + i);
                i++;
            }

        }
        System.out.println();

    }

    public static void printMemory() {
        System.out.println("Memory");
        for (int i = 0; i < memory.length / 5; i++) {
            int j = i + 20, k = i + 40, l = i + 60, m = i + 80;
            System.out.println(i + " " + memory[i] +
                            " \t " + j + " " + memory[j] +
                            " \t " + k + " " + memory[k] +
                            " \t " + l + " " + memory[l] +
                            " \t " + m + " " + memory[m]
            );
        }
    }

    //---------------------------------------------------INTERRUPT HANDLERS----------------------------------------------------------

    //job enters drum
    public static void Crint (int[] a, int[] p) {

        sos.ontrace();

        Job currJob = currentJob();
        saveJob(currJob, p[5]);

        setTableIndex();

        //store job in jobtable
        jobTable[tableIndex] = new Job(p[1], p[2], p[3], p[4], p[5], 7);

        swapJob();
        runJob(a, p);
    }

    //finished swapping job
    public static void Drmint (int[] a, int[] p) {

        Job currJob = currentJob();
        saveJob(currJob, p[5]);

        drumbusy = false;

        Job job = jobInTransit();
        job.inTransit = false;

        //swapped in, remove job from drumQueue
        if (swappingIN) {
            swappingIN = false;
            job.inCore = true;
            drumQueue.remove(new Integer(job.num));
            System.out.println(drumQueue);
            jobsInMemory.add(job);
        }
        //swapped out, add job to drumQueue
        else {
            jobsInMemory.remove(job);
            drumQueue.add(job.num);
            System.out.println(drumQueue);
            System.out.println();

        }

        //finish swapping jobs out for I/O job
        if (waitingOnDrum) {
            Job top = searchJobTable(diskQueue.peek());
            swapJobBackIn(top);
        }

        swapJob();
        runJob(a, p);
    }

    //system call
    public static void Svc (int[] a, int[] p) {

        Job currJob = currentJob();
        saveJob(currJob, p[5]);

        switch(a[0]) {
            case 5: //terminate
                if (currJob.doingIO || currJob.awaitingIO) currJob.kill = true;
                else terminate(currJob);
                break;
            case 6: //I/O
                requestIO(currJob);
                startIO();
                break;
            case 7: //block
                blockJob(currJob);
                break;
        }

        swapJob();
        runJob(a, p);
    }

    //finished I/O
    public static void Dskint (int[] a, int[] p) {

        Job currJob = currentJob();
        saveJob(currJob, p[5]);

        //job finished I/O
        Job ioJob = jobInIO();
        ioJob.doingIO = false;
        if (ioJob.kill) terminate(ioJob);
        if (ioJob.blocked) unblockJob(ioJob);
        diskbusy = false;

        //start another job I/O
        startIO();

        swapJob();
        runJob(a, p);
    }

    //job ran out of time
    public static void Tro (int[] a, int[] p) {

        Job currJob = currentJob();
        saveJob(currJob, p[5]);

        if (currJob.CPU_Usage == currJob.maxCPUTime) {

            if (currJob.doingIO || currJob.awaitingIO) currJob.kill = true;
            else terminate(currJob);
        }

        swapJob();
        runJob(a, p);
    }
}
