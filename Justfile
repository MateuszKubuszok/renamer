project-version := `git describe --tags`

#publish-maven-auth-options := "--user env:OSSRH_USERNAME --password env:OSSRH_PASSWORD --gpg-key $PGP_KEY_ID --gpg-option --pinentry-mode --gpg-option loopback --gpg-option --passphrase --gpg-option $PGP_PASSWORD"
publish-maven-auth-options := "--user env:OSSRH_USERNAME --password env:OSSRH_PASSWORD --gpg-key $PGP_KEY_ID --gpg-option --pinentry-mode --gpg-option loopback"

run:
	scala-cli run .

format:
	scala-cli format .

native-image:
	rm -rf renamer
	scala-cli --power package --native-image . -o renamer -- \
		-H:IncludeResources=libnative-arm64-darwin-crossterm.dylib \
		-H:IncludeResources=libnative-x86_64-darwin-crossterm.dylib \
		-H:IncludeResources=libnative-x86_64-linux-crossterm.so \
		-H:IncludeResources=native-x86_64-windows-crossterm.dll \
		--no-fallback

publish-maven:
	scala-cli --power publish . \
		--project-version {{project-version}} \
		{{publish-maven-auth-options}}

publish-local:
	scala-cli publish local . \
		--project-version {{project-version}}

coursier-install:
	coursier bootstrap --native-image \
		--graalvm graalvm-community:24.0.1 \
		com.kubuszok:renamer_3:{{project-version}} \
		-o renamer-coursier \
		--graalvm-opt --no-fallback \
		-- \
			-H:IncludeResources=libnative-arm64-darwin-crossterm.dylib \
			-H:IncludeResources=libnative-x86_64-darwin-crossterm.dylib \
			-H:IncludeResources=libnative-x86_64-linux-crossterm.so \
			-H:IncludeResources=native-x86_64-windows-crossterm.dll
