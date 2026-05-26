{
  description = "bb-tts - Babashka text-to-speech with Supertonic";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
    in {
      devShells.${system}.default = pkgs.mkShell {
        buildInputs = [
          pkgs.babashka
          pkgs.python313
          pkgs.stdenv.cc.cc.lib
          pkgs.zlib
          pkgs.libsndfile
        ];
        shellHook = ''
          export LD_LIBRARY_PATH="${pkgs.stdenv.cc.cc.lib}/lib:${pkgs.zlib}/lib:${pkgs.libsndfile}/lib:''${LD_LIBRARY_PATH:-}"
          if [ ! -d .venv ]; then
            python3 -m venv .venv
            .venv/bin/pip install 'supertonic[serve]' -q
          fi
        '';
      };
    };
}
