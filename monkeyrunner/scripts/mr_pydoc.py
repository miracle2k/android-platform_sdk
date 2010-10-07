#!/usr/bin/env monkeyrunner
# Copyright 2010, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
import com.android.monkeyrunner.MonkeyRunnerHelp as mrh
import pydoc
import sys

def create_page(title, document):
  return """
page.title=%s
@jd:body
%s
</body>
</html>
""" % (title, document)

BASEDIR = 'frameworks/base/docs/html/guide/topics/testing/'

def main():
  document = ""

  for clz in mrh.getAllDocumentedClasses():
    object, name = pydoc.resolve(str(clz), 0)
    document += pydoc.html.document(object, name)

  page = create_page('MonkeyRunner API', document)
  file = open(BASEDIR + 'monkeyrunner_api.html', 'w')
  file.write(page)
  file.close()

if __name__ == '__main__':
  main()
