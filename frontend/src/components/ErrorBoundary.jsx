import React, { Component } from "react";

/**
 * A chunk that fails to load is not really an error - it means the app was updated while this
 * page was open.
 *
 * Routes are code-split, so their filenames carry a content hash. After a deploy the old
 * filenames stop existing, and the next lazy route a stale tab navigates to throws
 * ChunkLoadError. Showing "Something went wrong" for that makes a routine, automatically
 * recoverable event look like a fault, and asks the user to work out that reloading fixes it.
 *
 * So a chunk error reloads itself - once. The guard matters: if the chunk is genuinely absent,
 * say a half-finished deploy, an unguarded reload would spin forever and the user would never
 * see why. One attempt, then the honest message.
 */
const RELOAD_FLAG = "cs-chunk-reload-attempted";

function isChunkLoadError(error) {
  if (!error) return false;
  const name = error.name || "";
  const message = error.message || "";
  return name === "ChunkLoadError"
    || /Loading chunk .* failed/i.test(message)
    || /Loading CSS chunk .* failed/i.test(message)
    || /error loading dynamically imported module/i.test(message);
}

class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null, staleBuild: false };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error, staleBuild: isChunkLoadError(error) };
  }

  componentDidCatch(error, errorInfo) {
    if (isChunkLoadError(error)) {
      // Reload once so the browser fetches the current build. sessionStorage rather than
      // state, because the reload throws this component away.
      if (!sessionStorage.getItem(RELOAD_FLAG)) {
        sessionStorage.setItem(RELOAD_FLAG, String(Date.now()));
        window.location.reload();
        return;
      }
      console.warn("A chunk failed to load again after reloading - the build may be incomplete.", error);
      return;
    }
    // A reload already succeeded at some point, so a later unrelated error should not be
    // treated as a second chunk failure.
    sessionStorage.removeItem(RELOAD_FLAG);
    console.error("ErrorBoundary caught:", error, errorInfo);
  }

  handleReload = () => {
    sessionStorage.removeItem(RELOAD_FLAG);
    this.setState({ hasError: false, error: null, staleBuild: false });
    window.location.reload();
  };

  render() {
    if (!this.state.hasError) return this.props.children;

    const { staleBuild, error } = this.state;
    return (
      <div style={{ padding: "2rem", textAlign: "center" }}>
        <h2>{staleBuild ? "This page is out of date" : "Something went wrong"}</h2>
        <p style={{ color: "#666", maxWidth: "34rem", margin: "0.5rem auto 0" }}>
          {staleBuild
            ? "The site was updated while this tab was open, and reloading did not pick up the "
              + "new version. Try again in a moment - nothing you were doing has been lost."
            : error?.message}
        </p>
        <button
          onClick={this.handleReload}
          style={{
            marginTop: "1rem", padding: "0.5rem 1.5rem", background: "#16a34a", color: "#fff",
            border: "none", borderRadius: "6px", cursor: "pointer",
          }}
        >
          Reload Page
        </button>
      </div>
    );
  }
}

export default ErrorBoundary;
