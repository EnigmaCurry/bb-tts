{
  description = "bb-tts - Babashka text-to-speech with Supertonic";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    supertonic.url = "github:EnigmaCurry/supertonic-py-flake";
    supertonic.inputs.nixpkgs.follows = "nixpkgs";
  };

  outputs = { self, nixpkgs, supertonic }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
      supertonic-serve = supertonic.packages.${system}.serve;
    in {
      packages.${system} = {
        default = pkgs.writeShellScriptBin "bb-tts" ''
          export PATH="${supertonic-serve}/bin:$PATH"
          exec ${pkgs.babashka}/bin/bb ${./bb-tts.clj} "$@"
        '';
        serve = supertonic-serve;
      };

      devShells.${system}.default = pkgs.mkShell {
        buildInputs = [
          pkgs.babashka
          supertonic-serve
        ];
      };
    };
}
