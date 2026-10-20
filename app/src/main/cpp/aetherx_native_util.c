#include "aetherx_native.hpp"

#include <ctype.h>
#include <stdint.h>

int aetherx_parse_u64(const char* text, uint64_t* out_value) {
    if (text == 0 || out_value == 0) return 0;

    const unsigned char* cursor = (const unsigned char*)text;
    while (*cursor != '\0' && isspace(*cursor)) cursor++;
    if (*cursor == '-') return 0;
    if (*cursor == '+') cursor++;
    if (*cursor < '0' || *cursor > '9') return 0;

    uint64_t value = 0;
    while (*cursor >= '0' && *cursor <= '9') {
        const uint64_t digit = (uint64_t)(*cursor - '0');
        if (value > (UINT64_MAX - digit) / 10U) return 0;
        value = value * 10U + digit;
        cursor++;
    }

    while (*cursor != '\0' && isspace(*cursor)) cursor++;
    if (*cursor != '\0') return 0;

    *out_value = value;
    return 1;
}
