
#include <message.h>

#include <stdlib.h>
#include <assert.h>

message::message(int size, int method_id, int execute_at) {
  this->size = size;
  this->method_id = method_id;
  this->execute_at = execute_at;
}

void message::read_params(netsocket *sock) {
  int param_size = size - 12;
  params = (int*)malloc(param_size);
  current = params;
  sock->read_chunk((char*)params, param_size);
}

int message::get_int_param() {
  return *((int*)(current++));
}

float message::get_float_param() {
  return *((float*)(current++));
}

message *message::push_on_stack(message *stack_top) {
  if (stack_top == NULL) {
    //previous = NULL;
    next = NULL;
    return this;
  } else {
    message *tmp = stack_top;
    while (tmp->next != NULL) tmp = tmp->next;
    tmp->next = this;
    next = NULL;
    //previous = tmp;
    return stack_top;
  }
}

message *message::remove_from_stack(message *stack_top) {
  assert(1==0);
  /*
  if (previous == NULL) {
    next = NULL;
    return next;
  } else {
    previous->next = next;
    previous = NULL;
    next = NULL;
    return stack_top;
  }
  */
}
