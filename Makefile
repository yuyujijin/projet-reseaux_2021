CC=gcc
CFLAGS=-W -Wunused-value -Wall -pthread
LDFLAGS= -lm -g

ALL=diffuseur

all: $(ALL)

diffuseur: src/diffuseur.c src/netradio.c
	$(CC) $(CFLAGS) -o $@ $^ $(LDFLAGS)

clean:
	rm -rf $(ALL)

cleano:
	rm -rf src/*.o

cleanall: clean cleano