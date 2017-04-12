#!/usr/bin/env bash

set -x
set -e

rm -r lib/mri
cp -r ../ruby/lib lib/mri
rm lib/mri/racc/rdoc/grammar.en.rdoc
rm lib/mri/timeout.rb
rm lib/mri/weakref.rb
rm -r lib/mri/webrick*
rm truffleruby/src/main/c/openssl/*.{c,h}
cp ../ruby/ext/openssl/*.{c,h} truffleruby/src/main/c/openssl
cp -r ../ruby/ext/openssl/lib/* lib/mri
cp -r ../ruby/ext/bigdecimal/lib/bigdecimal lib/mri
cp -r ../ruby/ext/psych/lib/psych lib/mri
cp -r ../ruby/ext/psych/lib/*.rb lib/mri
cp -r ../ruby/ext/pty/lib/*.rb lib/mri

rm -rf test/mri
cp -r ../ruby/test test/mri
cp -r ../ruby/ext/-test- test/mri
mkdir test/mri/cext
mv test/mri/-ext- test/mri/cext/ruby
mv test/mri/-test- test/mri/cext/c
find test/mri/cext/ruby -name *.rb | xargs -n 1 sed -i .backup 's/-test-/c/g'
find test/mri/cext/ruby -name *.backup | xargs rm
git checkout -- test/mri/excludes_truffle
rm -rf test/mri/excludes
git checkout -- test/mri/runner.rb

cp ../ruby/BSDL doc/legal/ruby-bsdl.txt
cp ../ruby/COPYING doc/legal/ruby-licence.txt
