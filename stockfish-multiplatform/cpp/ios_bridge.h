#ifndef IOS_BRIDGE_H
#define IOS_BRIDGE_H

#ifdef __cplusplus
extern "C" {
#endif

void stockfish_init(void);
void stockfish_send(const char* command);
const char* stockfish_read(void);
void stockfish_destroy(void);

#ifdef __cplusplus
}
#endif

#endif /* IOS_BRIDGE_H */
