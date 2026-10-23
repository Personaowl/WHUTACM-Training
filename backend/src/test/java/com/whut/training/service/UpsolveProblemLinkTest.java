package com.whut.training.service;

import com.whut.training.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UpsolveProblemLinkTest {
    @Test void commonPlatformLinksNormalizeWithoutFetchingPages() {
        assertThat(UpsolveProblemLink.normalize(" http://WWW.CODEFORCES.COM:80/contest/123/problem/a?locale=en#x "))
                .isEqualTo("https://codeforces.com/problemset/problem/123/A");
        assertThat(UpsolveProblemLink.normalize("https://codeforces.com/gym/100001/problem/A"))
                .isEqualTo("https://codeforces.com/gym/100001/problem/A");
        assertThat(UpsolveProblemLink.normalize("https://atcoder.jp/contests/abc380/tasks/abc380_f/?lang=en"))
                .isEqualTo("https://atcoder.jp/contests/abc380/tasks/abc380_f");
        assertThat(UpsolveProblemLink.normalize("http://luogu.com.cn/problem/P1001/"))
                .isEqualTo("https://www.luogu.com.cn/problem/P1001");
    }
    @Test void unrelatedSitesAndQueryParametersAreNotMistakenForCodeforces() {
        assertThat(UpsolveProblemLink.normalize("https://evilcodeforces.com/contest/123/problem/a?q=1#x"))
                .isEqualTo("https://evilcodeforces.com/contest/123/problem/a?q=1");
        assertThat(UpsolveProblemLink.normalize("https://ac.nowcoder.com/acm/problem/123?contestId=1"))
                .isEqualTo("https://ac.nowcoder.com/acm/problem/123?contestId=1");
        assertThat(UpsolveProblemLink.hash("https://example.com/A")).isNotEqualTo(UpsolveProblemLink.hash("https://example.com/a"));
    }
    @Test void unsafeOrMalformedLinksAreRejected() {
        for(String link:new String[]{"", "javascript:alert(1)","data:text/html,test","//example.com/a", "https://name:secret@example.com/a", "https://example.com:99999/a", "https://example.com/a b"}) {
            assertThatThrownBy(() -> UpsolveProblemLink.normalize(link)).isInstanceOf(BusinessException.class);
        }
    }
}
