
#ifndef __MEMSOCKET_H
#define __MEMSOCKET_H

#include <mysocket.h>
#include <pthread.h>
#include <stdlib.h>
#include <stdio.h>
#include <queue>

using namespace std;

class memsocket : public mysocket {

  queue<void*> free_buffers;
  pthread_mutex_t free_buffer_lock;
  pthread_cond_t free_buffer_release_cond;

  queue<void*> data_queue;
  pthread_mutex_t queue_lock;
  pthread_cond_t queue_push_cond;
  pthread_cond_t queue_pop_cond;

  int buffer_size;

 public:

  memsocket() {
    buffer_size = 0;
    pthread_mutex_init(&free_buffer_lock, NULL);
    pthread_cond_init(&free_buffer_release_cond, NULL);
    pthread_mutex_init(&queue_lock, NULL);
    pthread_cond_init(&queue_push_cond, NULL);
    pthread_cond_init(&queue_pop_cond, NULL);
  }


  virtual void set_buffer_size(int size) {

    if (buffer_size == 0) {

      pthread_mutex_lock(&free_buffer_lock);
      for (int y = 0; y < 25; y++) {
	free_buffers.push(malloc(size));
      }
      pthread_cond_signal(&free_buffer_release_cond);
      pthread_mutex_unlock(&free_buffer_lock);


    } else {
      if (size != buffer_size) {
	fprintf(stderr, "error: not alowed to change the buffer size form memsocket!");
	exit(0);
      }
    }
  }
  
  virtual bool is_mem_socket() { return true; }
  virtual bool is_net_socket() { return false; }
  virtual void close() {}

  void *get_free_buffer() {
    void *res;
    pthread_mutex_lock(&free_buffer_lock);
    if (free_buffers.size() == 0) {
      pthread_cond_wait(&free_buffer_release_cond, &free_buffer_lock);
    }
    res = free_buffers.front();
    free_buffers.pop();
    pthread_mutex_unlock(&free_buffer_lock);
    return res;
  }  

  void release_buffer(void *buf) {
    pthread_mutex_lock(&free_buffer_lock);
    free_buffers.push(buf);
    pthread_cond_signal(&free_buffer_release_cond);
    pthread_mutex_unlock(&free_buffer_lock);
  }

  inline int queue_size() {
    int size;
    pthread_mutex_lock(&queue_lock);
    size = data_queue.size();
    pthread_mutex_unlock(&queue_lock);
    return size;
  }

  inline bool queue_empty() {
    return queue_size() == 0;
  }

  inline bool queue_full() {
    return queue_size() >= 24;
  }
  
  inline void wait_for_data() {
    pthread_mutex_lock(&queue_lock);
    pthread_cond_wait(&queue_push_cond, &queue_lock);
    pthread_mutex_unlock(&queue_lock);
    return;
  }

  inline void wait_for_space() {
    pthread_mutex_lock(&queue_lock);
    pthread_cond_wait(&queue_pop_cond, &queue_lock);
    pthread_mutex_unlock(&queue_lock);
    return;
  }  
  
  inline void push_buffer(void *ptr) {
    pthread_mutex_lock(&queue_lock);
    data_queue.push(ptr);
    pthread_cond_signal(&queue_push_cond);
    pthread_mutex_unlock(&queue_lock);
  }

  inline void* pop_buffer() {
    void *res;
    pthread_mutex_lock(&queue_lock);
    res = data_queue.front();
    data_queue.pop();
    pthread_cond_signal(&queue_pop_cond);
    pthread_mutex_unlock(&queue_lock);
    return res;
  }  

  
  
  


};

#endif


