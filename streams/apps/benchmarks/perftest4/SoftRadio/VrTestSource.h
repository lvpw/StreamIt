#ifndef _VRTESTSOURCE_H_
#define _VRTESTSOURCE_H_

#include <VrSource.h>
#include <limits.h>

class VrTestSource : public VrSource<char> {
protected:
  char c;
public:
  virtual void work(int i) {
    //printf("TestSource %d\n", i);
    for (int n = 0; n < i; n ++) {
      //printf("TestSource Writing 1\n");
      outputWrite((char)c);

      if (c == CHAR_MAX)
	c = (char) 0;
      else
	c++;
    }
  }
  virtual void initialize() {
    setOutputSize (getFirstConnector()->getHistory());
  }
};
#endif
