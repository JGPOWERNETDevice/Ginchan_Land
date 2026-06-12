#ifndef COMPAT_INIPARSER_H_
#define COMPAT_INIPARSER_H_

#ifdef __cplusplus
extern "C" {
#endif

typedef struct _dictionary_ dictionary;

static inline int iniparser_getint(dictionary* d, const char* key, int def) {
    (void)d;
    (void)key;
    return def;
}

static inline double iniparser_getdouble(dictionary* d, const char* key, double def) {
    (void)d;
    (void)key;
    return def;
}

static inline const char* iniparser_getstring(dictionary* d, const char* key, const char* def) {
    (void)d;
    (void)key;
    return def;
}

#ifdef __cplusplus
}
#endif

#endif
