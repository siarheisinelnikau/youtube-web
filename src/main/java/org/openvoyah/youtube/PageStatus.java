package org.openvoyah.youtube;

/** What the page area shows: the loading screen, the page itself or the reason the page cannot show. */
abstract class PageStatus {
    /** A page load is under way and nothing of it is visible yet. */
    static final PageStatus LOADING = new Plain("Loading");
    /** The page is visible. */
    static final PageStatus SHOWN = new Plain("Shown");

    private PageStatus() {}

    private static final class Plain extends PageStatus {
        private final String name;

        Plain(String name) {
            this.name = name;
        }

        @Override public String toString() {
            return name;
        }
    }

    static final class Failed extends PageStatus {
        final Problem problem;
        final String detail;

        Failed(Problem problem) {
            this(problem, "");
        }

        Failed(Problem problem, String detail) {
            this.problem = problem;
            this.detail = detail;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Failed)) return false;
            Failed that = (Failed) other;
            return problem == that.problem && detail.equals(that.detail);
        }

        @Override public int hashCode() {
            return 31 * problem.hashCode() + detail.hashCode();
        }

        @Override public String toString() {
            return "Failed(problem=" + problem + ", detail=" + detail + ")";
        }
    }
}
