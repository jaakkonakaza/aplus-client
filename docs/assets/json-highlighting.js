(function () {
    function highlightJson() {
        // Provided by Dokka's bundled Prism script.
        // noinspection JSUnresolvedReference
        const prism = window.Prism;
        if (!prism) return;

        prism.languages.json = {
            property: /"(?:\\.|[^"\\\r\n])*"(?=\s*:)/,
            string: /"(?:\\.|[^"\\\r\n])*"/,
            number: /-?\b\d+(?:\.\d+)?(?:e[+-]?\d+)?\b/i,
            boolean: /\b(?:true|false)\b/,
            null: { pattern: /\bnull\b/, alias: "keyword" },
            punctuation: /[{}\[\],]/,
            operator: /:/
        };
        document.querySelectorAll("code.lang-json, code.language-json").forEach(function (code) {
            prism.highlightElement(code);
        });
    }

    // Dokka loads Prism asynchronously. The load event waits for it to finish.
    if (document.readyState === "complete") {
        highlightJson();
    } else {
        window.addEventListener("load", highlightJson, { once: true });
    }
}());
