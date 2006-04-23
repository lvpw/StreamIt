#ifndef __CCP_H
#define __CCP_H

#include <netsocket.h>
#include <ccp_session.h>

#include <vector>
#include <map>

using namespace std;

class ccp {

  int number_of_threads;
  int machines_in_partition;
  map<int, int> partition;         // from thread to machine id

  map<int, unsigned> machines;     // from machine id to ip address

  map<int, int> threadusage;	   // DB_COMMENT from thread to usage

  map<int, int> threadCpuUtil;	   // DB_COMMENT from thread to cpu utilization

  multimap<int, int> machineTothread;   // DB_COMMENT from machine to thread

  void find_thread_per_machine_mapping();  // DB_COMMENT create the multimap


  bool waiting_to_start_execution;
  int initial_iteration;

  vector <ccp_session*> sessions;
  
  int read_config_file(char *fname);
  int read_work_estimate_file(char *file_name);
  void assign_nodes_to_partition();

  void send_cluster_config(int iter = 0);

  void handle_change_in_number_of_nodes();
  void execute_partitioner(int number_of_nodes);
  // DB_COMMENT
  void cpu_utilization_per_thread();
 public:

  ccp();

  void set_init_iter(int iter);

  int run_ccp();

};


#endif
